package app

import (
	"context"
	"database/sql"
)

func PushQueue(ctx context.Context, db *sql.DB, gameID string) error {
	return nil
}

func PeekQueue(ctx context.Context, db *sql.DB) (OthelloGame, error) {
	return OthelloGame{}, nil
}

func AckQueue(ctx context.Context, db *sql.DB, gameID string) error {
	return nil
}
