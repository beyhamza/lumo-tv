#!/bin/sh
# Bench multi-track media (S15-05, rest of S13-00): /stream/multi/ and
# /film/le-phare-multipistes.mkv. Colour bars, test pattern and tones only;
# nothing anybody owns. Run from this directory:
#
#   docker run --rm --entrypoint sh -v "$PWD:/b" -e OUT=/b/fixtures-out \
#     jrottenberg/ffmpeg:6-alpine /b/make-multitrack.sh
#
# then copy fixtures-out/multi to fixtures/stream/multi and fixtures-out/film
# to fixtures/film.
set -eu
O=${OUT:-/out}
D=12
mkdir -p $O/multi/v240 $O/multi/v480 $O/multi/a-fr $O/multi/a-en $O/multi/s-fr $O/multi/s-en $O/film
HLS="-f hls -hls_time 4 -hls_playlist_type vod -hls_segment_filename"
ffmpeg -hide_banner -loglevel error -f lavfi -i "smptebars=size=426x240:rate=25:duration=$D" \
  -c:v libx264 -preset veryfast -pix_fmt yuv420p -profile:v baseline -level 3.0 -b:v 200k -g 100 -keyint_min 100 -sc_threshold 0 -an \
  $HLS "$O/multi/v240/seg%d.ts" $O/multi/v240/index.m3u8
ffmpeg -hide_banner -loglevel error -f lavfi -i "testsrc2=size=854x480:rate=25:duration=$D" \
  -c:v libx264 -preset veryfast -pix_fmt yuv420p -profile:v main -level 3.1 -b:v 300k -maxrate 330k -bufsize 600k -g 100 -keyint_min 100 -sc_threshold 0 -an \
  $HLS "$O/multi/v480/seg%d.ts" $O/multi/v480/index.m3u8
ffmpeg -hide_banner -loglevel error -f lavfi -i "sine=frequency=440:duration=$D" -c:a aac -b:a 64k -ac 2 \
  $HLS "$O/multi/a-fr/seg%d.ts" $O/multi/a-fr/index.m3u8
ffmpeg -hide_banner -loglevel error -f lavfi -i "sine=frequency=880:duration=$D" -c:a aac -b:a 64k -ac 2 \
  $HLS "$O/multi/a-en/seg%d.ts" $O/multi/a-en/index.m3u8
for l in fr en; do
  if [ $l = fr ]; then t="Sous-titre de banc"; else t="Bench subtitle"; fi
  { echo "WEBVTT"; echo "X-TIMESTAMP-MAP=MPEGTS:126000,LOCAL:00:00:00.000"; echo;
    for i in 0 1 2 3; do s=$((i*3)); e=$((s+3));
      printf '00:00:%02d.000 --> 00:00:%02d.000\n%s %d\n\n' $s $e "$t" $((i+1)); done; } > $O/multi/s-$l/sub.vtt
  printf '#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-TARGETDURATION:%d\n#EXT-X-PLAYLIST-TYPE:VOD\n#EXTINF:%d.0,\nsub.vtt\n#EXT-X-ENDLIST\n' $D $D > $O/multi/s-$l/index.m3u8
  { printf '1\n00:00:00,000 --> 00:00:06,000\n%s 1\n\n2\n00:00:06,000 --> 00:00:12,000\n%s 2\n' "$t" "$t"; } > /tmp/$l.srt
done
cat > $O/multi/master.m3u8 <<M3U
#EXTM3U
#EXT-X-VERSION:4
#EXT-X-INDEPENDENT-SEGMENTS
#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="aud",NAME="Français",LANGUAGE="fr",DEFAULT=YES,AUTOSELECT=YES,CHANNELS="2",URI="a-fr/index.m3u8"
#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="aud",NAME="English",LANGUAGE="en",DEFAULT=NO,AUTOSELECT=YES,CHANNELS="2",URI="a-en/index.m3u8"
#EXT-X-MEDIA:TYPE=SUBTITLES,GROUP-ID="sub",NAME="Français",LANGUAGE="fr",DEFAULT=NO,AUTOSELECT=YES,FORCED=NO,URI="s-fr/index.m3u8"
#EXT-X-MEDIA:TYPE=SUBTITLES,GROUP-ID="sub",NAME="English",LANGUAGE="en",DEFAULT=NO,AUTOSELECT=YES,FORCED=NO,URI="s-en/index.m3u8"
#EXT-X-STREAM-INF:BANDWIDTH=300000,AVERAGE-BANDWIDTH=270000,RESOLUTION=426x240,FRAME-RATE=25.000,CODECS="avc1.42e01e,mp4a.40.2",AUDIO="aud",SUBTITLES="sub"
v240/index.m3u8
#EXT-X-STREAM-INF:BANDWIDTH=420000,AVERAGE-BANDWIDTH=380000,RESOLUTION=854x480,FRAME-RATE=25.000,CODECS="avc1.4d401f,mp4a.40.2",AUDIO="aud",SUBTITLES="sub"
v480/index.m3u8
M3U
ffmpeg -hide_banner -loglevel error -f lavfi -i "smptebars=size=320x180:rate=25:duration=$D" \
  -f lavfi -i "sine=frequency=440:duration=$D" -f lavfi -i "sine=frequency=880:duration=$D" \
  -i /tmp/fr.srt -i /tmp/en.srt \
  -map 0:v -map 1:a -map 2:a -map 3:s -map 4:s \
  -c:v libx264 -preset veryfast -pix_fmt yuv420p -profile:v baseline -level 3.0 -b:v 150k \
  -c:a aac -b:a 48k -c:s srt \
  -metadata:s:a:0 language=fre -metadata:s:a:0 title="Français" \
  -metadata:s:a:1 language=eng -metadata:s:a:1 title="English" \
  -metadata:s:s:0 language=fre -metadata:s:s:0 title="Français" \
  -metadata:s:s:1 language=eng -metadata:s:s:1 title="English" \
  -disposition:a:0 default -disposition:a:1 0 -disposition:s:0 0 -disposition:s:1 0 \
  -shortest -y $O/film/le-phare-multipistes.mkv
ls -la $O/multi/* $O/film
