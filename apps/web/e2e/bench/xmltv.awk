# =============================================================================
# XMLTV bench guide generator (S9-07-03, I-1/I-2/I-6).
#
# Every date is relative to `anchor` (epoch seconds, UTC). No absolute date is
# committed, so the guide can never expire; entrypoint.sh regenerates each file
# at every `up` and nginx serves them read-only.
#
# Run under TZ=UTC: strftime then prints the "+0000" offset XMLTV requires.
#
# The calendar is D-1 to D+3 around the anchor, the same retention window the
# API's XmltvStreamParser keeps (`anchor - 1 day` .. `anchor + 3 days`).
# Identifiers are those of playlist.m3u / mixed.m3u (`bench.1`..`bench.4`), NOT
# the `epg-bench-N` of EpgBenchFixtures (S9-00) which no bench channel carries.
#
# Variants (the `variant` variable):
#   canonical   Game A, docs/releases/0.2.0/s9-07-recette.md §2.2
#   transition  Game B (§2.3): E1 about to end on bench.1, E2 just after
#   partial     canonical without bench.3, so its column is a gap, not an error
#   empty       <tv></tv>, no programme at all
#   broken      well-formed opening, then an element never closed: import FAILS
#   stale       same bytes as canonical (staleness is the DB timestamp, §2.4)
#   big         100 channels bench.001..bench.100, 30 min, D-1..D+3 (< 4 MiB)
#   huge        big, three hours only, 8192-char descriptions (> 4 MiB)
# =============================================================================

function stamp(epoch) { return strftime("%Y%m%d%H%M%S +0000", epoch) }

function programme(ch, start, durationMin, title, desc,    e) {
    e = start + durationMin * 60
    printf "<programme channel=\"%s\" start=\"%s\" stop=\"%s\"><title>%s</title>", \
        ch, stamp(start), stamp(e), title
    if (desc != "") printf "<desc>%s</desc>", desc
    printf "<category>Bench</category></programme>\n"
}

function channel(id, name) {
    printf "<channel id=\"%s\"><display-name>%s</display-name></channel>\n", id, name
}

# Thirty-minute blocks over [from, to) on `ch`. A block whose start falls inside
# [sf1, st1) or [sf2, st2) is left out: that is where a named programme sits, or
# where the protocol asks for a gap. Pass sf2 = st2 = 0 for "no second skip".
function fill(ch, from, to, label, sf1, st1, sf2, st2,    s, i) {
    s = from
    i = 0
    while (s < to) {
        if (!(s >= sf1 && s < st1) && !(s >= sf2 && s < st2)) {
            programme(ch, s, 30, label " " i, "")
        }
        s += 1800
        i++
    }
}

BEGIN {
    bigdesc = ""
    for (k = 0; k < 8192; k++) bigdesc = bigdesc "x"

    from = anchor - 86400
    to = anchor + 3 * 86400

    print "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
    print "<tv generator-info-name=\"lumo-bench\">"

    if (variant == "empty") {
        print "</tv>"
        exit 0
    }

    if (variant == "broken") {
        # A valid opening then an element that is never closed. The API's StAX
        # parser raises XMLStreamException and the import must end FAILED.
        channel("bench.1", "Chaîne 01 FHD")
        programme("bench.1", anchor, 45, "A1", "")
        printf "<broken"
        exit 0
    }

    if (variant == "big" || variant == "huge") {
        # Game D: 100 channels aligned with playlist-100.m3u, 30-minute blocks.
        # `huge` keeps three hours only but fills every description with 8 192
        # characters, which is what takes the file past 4 MiB.
        heavy = (variant == "huge")
        last = heavy ? anchor + 3 * 3600 : to
        first = heavy ? anchor : from
        for (c = 1; c <= 100; c++) {
            id = sprintf("bench.%03d", c)
            channel(id, sprintf("Chaîne %03d", c))
        }
        for (c = 1; c <= 100; c++) {
            id = sprintf("bench.%03d", c)
            s = first
            i = 0
            while (s < last) {
                desc = heavy ? bigdesc : ""
                programme(id, s, 30, sprintf("Programme %s %d", id, i), desc)
                s += 1800
                i++
            }
        }
        print "</tv>"
        exit 0
    }

    # canonical / transition / partial / stale: the five bench channels. Chaîne
    # 05 has no tvg-id, so it is deliberately absent: no guide, as the protocol
    # asks, and the channel stays readable in Chaînes.
    channel("bench.1", "Chaîne 01 FHD")
    channel("bench.2", "Chaîne 02")
    if (variant != "partial") channel("bench.3", "Chaîne 03 HD")
    channel("bench.4", "Chaîne 04 4K")

    if (variant == "transition") {
        # Game B: the sheet of E1 is opened before its end, then the clock moves
        # and E2 becomes current (GD-07/08). Filled around the pair so the guide
        # is not empty outside the sequence.
        programme("bench.1", anchor - 1800, 32, "E1", "")
        programme("bench.1", anchor + 180, 30, "E2", "")
        fill("bench.1", from, to, "T", anchor - 1800, anchor + 1980)
    } else {
        # Game A. Named programmes first, then the 30-minute fill around them, so
        # navigation at reference T+25 finds A1 (bench.1), B1 (bench.2), C1
        # (bench.3) and a gap on bench.4 (`lacune à T+25`).
        programme("bench.1", anchor, 45, "A1", "")
        programme("bench.1", anchor + 2700, 45, "A2", "")
        programme("bench.2", anchor + 900, 15, "B1", "")
        programme("bench.2", anchor + 1800, 60, "B2", "")
        if (variant != "partial") programme("bench.3", anchor, 60, "C1", "")
        programme("bench.4", anchor + 7200, 60, "D1", "")

        fill("bench.1", from, to, "A", anchor, anchor + 5400, 0, 0)
        fill("bench.2", from, to, "B", anchor, anchor + 5400, 0, 0)
        if (variant != "partial") {
            fill("bench.3", from, to, "C", anchor, anchor + 3600, 0, 0)
        }
        fill("bench.4", from, to, "D", anchor, anchor + 1800, anchor + 7200, anchor + 10800)
    }

    print "</tv>"
}
