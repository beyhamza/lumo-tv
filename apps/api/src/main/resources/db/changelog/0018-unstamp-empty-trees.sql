--liquibase formatted sql

--changeset hamza:0018-01-unstamp-empty-trees
--comment An empty answer was stored as a fact. Let those series be fetched again.
--
-- `readTree` had no guard on the document itself: an empty body — or `[]`, or any
-- JSON that is not a series sheet — walked through both loops zero times and came
-- back as an EMPTY LIST rather than as "no answer". The caller stored it and
-- stamped `tree_fetched_at`, so a moment of transport failure became a recorded
-- fact: "this series has no episodes", cached for six hours.
--
-- It was reachable because `get_series_info` was addressed with `vod_id`, which a
-- strict panel answers with nothing. Both are fixed. What neither fix undoes is
-- the rows already written, and a stamped row is precisely one that will NOT be
-- fetched again — the emptiness would outlive its cause by six hours per series,
-- and the person looking at the screen has no way to ask for a retry.
--
-- Clearing the stamp is enough. The tree itself is empty, so there is nothing to
-- delete; the next time somebody opens one of these series it is fetched, and
-- this time the answer is either a tree or an honest failure.
--
-- **A series a panel genuinely lists with no seasons is cleared too**, and that is
-- accepted rather than overlooked: nothing in the row distinguishes the two, and
-- the cost of being wrong is one extra request the next time it is opened. The
-- cost the other way is a series that stays wrong.
UPDATE series
   SET tree_fetched_at = NULL
 WHERE tree_fetched_at IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM season WHERE season.series_id = series.id);

--rollback SELECT 1;
