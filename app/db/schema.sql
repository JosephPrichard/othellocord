CREATE TABLE IF NOT EXISTS stats (
    player_id TEXT PRIMARY KEY,
    elo FLOAT NOT NULL,
    won INTEGER NOT NULL,
    drawn INTEGER NOT NULL,
    lost INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS games (
    id TEXT NOT NULL,
    board TEXT NOT NULL,
    white_id TEXT NOT NULL,
    black_id TEXT NOT NULL,
    white_name TEXT NOT NULL,
    black_name TEXT NOT NULL,
    moves TEXT NOT NULL,
    expire_time INTEGER NOT NULL,
    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_stats_elo ON stats(elo);
CREATE INDEX IF NOT EXISTS idx_games_expire_time ON games(expire_time);
CREATE INDEX IF NOT EXISTS idx_games_player_ids ON games(white_id, black_id);