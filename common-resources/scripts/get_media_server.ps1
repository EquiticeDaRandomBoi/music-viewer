# get_media_server.ps1 - persistent SMTC polling server
# Stays alive; Java writes "poll" on stdin, script writes one JSON line per poll on stdout.
param()
# Ensure Unicode output so non-Latin titles (Japanese, Korean, etc.) survive the pipe.
# UTF-8 without BOM — [System.Text.Encoding]::UTF8 emits a BOM on .NET Framework (PS 5.1)
# which confuses JSON parsers reading the pipe.
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)
$OutputEncoding          = [System.Text.UTF8Encoding]::new($false)

Add-Type -AssemblyName System.Runtime.WindowsRuntime

$asTaskGeneric = ([System.WindowsRuntimeSystemExtensions].GetMethods() |
    Where-Object {
        $_.Name -eq 'AsTask' -and
        $_.GetParameters().Count -eq 1 -and
        $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1'
    })[0]

# Reflect AsStreamForRead — PS can't dispatch extension methods on __ComObject directly,
# but reflection bypasses type-check and lets CLR do the COM QI internally.
$asStreamForReadMethod = [System.IO.WindowsRuntimeStreamExtensions].GetMethods() |
    Where-Object { $_.Name -eq 'AsStreamForRead' -and $_.GetParameters().Count -eq 1 }

function Await($WinRtTask, $ResultType) {
    $asTask  = $asTaskGeneric.MakeGenericMethod($ResultType)
    $netTask = $asTask.Invoke($null, @($WinRtTask))
    if (-not $netTask.Wait(5000)) { throw 'WinRT timeout' }
    $netTask.Result
}

[void][Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager,Windows.Media.Control,ContentType=WindowsRuntime]
[void][Windows.Storage.Streams.IRandomAccessStreamWithContentType,Windows.Storage,ContentType=WindowsRuntime]

try {
    $manager = Await `
        ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) `
        ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])
} catch {
    exit 1
}

$tempDir   = [System.IO.Path]::Combine([System.IO.Path]::GetTempPath(), 'musicplayer')
[System.IO.Directory]::CreateDirectory($tempDir) | Out-Null
$tempThumb = [System.IO.Path]::Combine($tempDir, 'thumb.png')

Add-Type -AssemblyName 'PresentationCore'

# Remove any stale thumbnail from a previous session so the old encoding is never loaded.
if (Test-Path $tempThumb) { Remove-Item $tempThumb -ErrorAction SilentlyContinue }

$lastTitle    = $null
$lastSource   = $null
$thumbPath    = ''
$thumbFetched = $false   # retry each poll until thumbnail successfully written
$thumbDelay   = 0        # polls to skip after a track change so SMTC thumbnail catches up
$thumbLog     = [System.IO.Path]::Combine($tempDir, 'thumb_ps_debug.log')

while ($true) {
    $line = [Console]::In.ReadLine()
    if ($null -eq $line) { break }
    if ($line.Trim() -ne 'poll') { continue }

    try {
        # Pick actively playing session; prefer dedicated music apps over browsers.
        $pbStatus       = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionPlaybackStatus]
        $session        = $null
        $playingBrowser = $null
        $pausedSession  = $null
        foreach ($s in $manager.GetSessions()) {
            $pb        = $s.GetPlaybackInfo()
            $src       = [string]$s.SourceAppUserModelId
            $isBrowser = $src -match 'vivaldi|chrome|msedge|firefox|opera|brave'
            if ($pb.PlaybackStatus -eq $pbStatus::Playing) {
                if (-not $isBrowser -and $session        -eq $null) { $session        = $s }
                elseif ($isBrowser  -and $playingBrowser -eq $null) { $playingBrowser = $s }
            } elseif ($pb.PlaybackStatus -eq $pbStatus::Paused -and $pausedSession -eq $null) {
                $pausedSession = $s
            }
        }
        if ($session -eq $null) { $session = $playingBrowser }
        if ($session -eq $null) { $session = $pausedSession  }
        if ($session -eq $null) { $session = $manager.GetCurrentSession() }
        if ($null -eq $session) {
            [Console]::Out.WriteLine('{}')
            [Console]::Out.Flush()
            $lastTitle = $null; $lastSource = $null; $thumbPath = ''; $thumbFetched = $false
            continue
        }

        $props    = Await ($session.TryGetMediaPropertiesAsync()) `
                         ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])
        $timeline = $session.GetTimelineProperties()
        $playback = $session.GetPlaybackInfo()

        $title    = if ($props.Title)               { [string]$props.Title }               else { '' }
        $artist   = if ($props.Artist)              { [string]$props.Artist }              else { '' }
        $sourceId = if ($session.SourceAppUserModelId) { [string]$session.SourceAppUserModelId } else { '' }

        # Reset thumbnail state when song or source app changes.
        if ($title -ne $lastTitle -or $sourceId -ne $lastSource) {
            $lastTitle    = $title
            $lastSource   = $sourceId
            $thumbPath    = ''
            $thumbFetched = $false
            # Wait 4 polls (~800 ms) before fetching so SMTC has time to push the
            # new artwork. Fetching immediately grabs the previous track's thumbnail.
            $thumbDelay   = 4
            "$(Get-Date -F 'HH:mm:ss') track change: '$title' / '$sourceId'" |
                Add-Content $thumbLog -EA SilentlyContinue
        }

        # Fetch thumbnail on every poll until successful (handles first-poll failures
        # and non-JPEG formats like WebP from browsers — WIC re-encodes to JPEG).
        if ($thumbDelay -gt 0) {
            $thumbDelay--
        } elseif (-not $thumbFetched -and $null -ne $props.Thumbnail) {
            try {
                $stream    = Await ($props.Thumbnail.OpenReadAsync()) `
                                   ([Windows.Storage.Streams.IRandomAccessStreamWithContentType])
                $netStream = $asStreamForReadMethod.Invoke($null, [object[]]@($stream))
                $ms        = [System.IO.MemoryStream]::new()
                $netStream.CopyTo($ms)
                $rawBytes = $ms.ToArray()
                if ($rawBytes.Length -gt 0) {
                    # BitmapDecoder.Create is MTA-safe (unlike BitmapImage which needs STA).
                    try {
                        $srcMs   = [System.IO.MemoryStream]::new($rawBytes)
                        $decoder = [System.Windows.Media.Imaging.BitmapDecoder]::Create(
                            $srcMs,
                            [System.Windows.Media.Imaging.BitmapCreateOptions]::None,
                            [System.Windows.Media.Imaging.BitmapCacheOption]::OnLoad
                        )
                        $src = $decoder.Frames[0]

                        # FormatConvertedBitmap converts to Rgb24 so PNG bytes are R,G,B order.
                        # WPF decodes to Bgra32 internally; without this stb_image swaps R<->B.
                        $rgb = [System.Windows.Media.Imaging.FormatConvertedBitmap]::new(
                            $src, [System.Windows.Media.PixelFormats]::Rgb24, $null, 0)
                        $pw = $rgb.PixelWidth
                        $ph = $rgb.PixelHeight
                        "$(Get-Date -F 'HH:mm:ss') decoded ${pw}x${ph} rawBytes=$($rawBytes.Length)" |
                            Add-Content $thumbLog -EA SilentlyContinue

                        # Letterbox to square: full thumbnail visible with black bars rather
                        # than a center crop. CopyPixels forces WPF to realise the lazy bitmap.
                        $side      = [Math]::Max($pw, $ph)   # square = larger dimension
                        $ox        = [int](($side - $pw) / 2)
                        $oy        = [int](($side - $ph) / 2)
                        $bpp       = 3
                        $srcStride = $pw * $bpp
                        $dstStride = $side * $bpp
                        $srcPix    = New-Object byte[] ($ph * $srcStride)
                        $rgb.CopyPixels($srcPix, $srcStride, 0)
                        $dstPix    = New-Object byte[] ($side * $dstStride)  # zero = black bars
                        for ($row = 0; $row -lt $ph; $row++) {
                            $si = $row * $srcStride
                            $di = ($oy + $row) * $dstStride + $ox * $bpp
                            [System.Array]::Copy($srcPix, $si, $dstPix, $di, $pw * $bpp)
                        }

                        $wb = [System.Windows.Media.Imaging.WriteableBitmap]::new(
                            $side, $side, 96, 96, [System.Windows.Media.PixelFormats]::Rgb24, $null)
                        $wb.WritePixels(
                            [System.Windows.Int32Rect]::new(0, 0, $side, $side),
                            $dstPix, $dstStride, 0)
                        $wb.Freeze()

                        $enc = [System.Windows.Media.Imaging.PngBitmapEncoder]::new()
                        $enc.Frames.Add([System.Windows.Media.Imaging.BitmapFrame]::Create($wb))
                        $ms2 = [System.IO.MemoryStream]::new()
                        $enc.Save($ms2)
                        $pngBytes = $ms2.ToArray()
                        "$(Get-Date -F 'HH:mm:ss') encoded ${side}x${side} png=$($pngBytes.Length) bytes" |
                            Add-Content $thumbLog -EA SilentlyContinue
                        if ($pngBytes.Length -gt 0) {
                            [System.IO.File]::WriteAllBytes($tempThumb, $pngBytes)
                            $thumbPath    = $tempThumb
                            $thumbFetched = $true
                            "$(Get-Date -F 'HH:mm:ss') saved OK -> $tempThumb" |
                                Add-Content $thumbLog -EA SilentlyContinue
                        }
                    } catch {
                        "$(Get-Date -F 'HH:mm:ss') ENCODE ERROR: $_" |
                            Add-Content $thumbLog -EA SilentlyContinue
                    }
                }
            } catch {
                "$(Get-Date -Format 'HH:mm:ss') thumb error: $_" |
                    Add-Content "$env:TEMP\musicplayer\thumb_debug.log" -ErrorAction SilentlyContinue
            }
        }

        $status    = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionPlaybackStatus]
        $isPlaying = ($playback.PlaybackStatus -eq $status::Playing)
        $posMs     = [long]$timeline.Position.TotalMilliseconds
        $durMs     = [long]$timeline.EndTime.TotalMilliseconds

        $result = [ordered]@{
            title         = $title
            artist        = $artist
            isPlaying     = $isPlaying
            positionMs    = $posMs
            durationMs    = $durMs
            thumbnailPath = $thumbPath
            sourceApp     = $sourceId
        } | ConvertTo-Json -Compress

        [Console]::Out.WriteLine($result)
        [Console]::Out.Flush()
    } catch {
        [Console]::Out.WriteLine('{}')
        [Console]::Out.Flush()
    }
}
