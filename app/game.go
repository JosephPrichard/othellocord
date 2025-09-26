package app

import (
	"context"
	"database/sql"
	"errors"
	"fmt"
	"github.com/google/uuid"
	"log/slog"
	"slices"
	"time"
)

type OthelloGame struct {
	ID          string
	Board       OthelloBoard
	WhitePlayer Player
	BlackPlayer Player
	MoveList    []Tile
}

func (o *OthelloGame) MakeMove(move Tile) {
	o.Board.MakeMove(move)
	o.MoveList = append(o.MoveList, move)
}

func (o *OthelloGame) TrySkipTurn() {
	if len(o.Board.FindCurrentMoves()) == 0 {
		o.Board.IsBlackMove = !o.Board.IsBlackMove
	}
}

func (o *OthelloGame) IsGameOver() bool {
	return len(o.Board.FindCurrentMoves()) == 0
}

func (o *OthelloGame) CurrentPlayer() Player {
	if o.Board.IsBlackMove {
		return o.BlackPlayer
	} else {
		return o.WhitePlayer
	}
}

func (o *OthelloGame) OtherPlayer() Player {
	if o.Board.IsBlackMove {
		return o.WhitePlayer
	} else {
		return o.BlackPlayer
	}
}

func (o *OthelloGame) CreateResult() GameResult {
	diff := o.Board.BlackScore() - o.Board.WhiteScore()
	if diff > 0 {
		return GameResult{Winner: o.BlackPlayer, Loser: o.WhitePlayer, IsDraw: false}
	} else if diff < 0 {
		return GameResult{Winner: o.WhitePlayer, Loser: o.BlackPlayer, IsDraw: false}
	} else {
		return GameResult{Winner: o.BlackPlayer, Loser: o.WhitePlayer, IsDraw: true}
	}
}

func (o *OthelloGame) CreateForfeitResult(forfeitId string) GameResult {
	if o.WhitePlayer.ID == forfeitId {
		return GameResult{Winner: o.BlackPlayer, Loser: o.WhitePlayer, IsDraw: false}
	} else if o.BlackPlayer.ID == forfeitId {
		return GameResult{Winner: o.WhitePlayer, Loser: o.BlackPlayer, IsDraw: false}
	} else {
		return GameResult{IsDraw: true}
	}
}

type GameResult struct {
	Winner Player
	Loser  Player
	IsDraw bool
}

const GameStoreTtl = time.Hour * 24

var ErrGameNotFound = errors.New("game not found")

func GetGame(ctx context.Context, q Query, playerID string) (OthelloGame, error) {
	trace := ctx.Value(TraceKey)
	fail := func(err error) (OthelloGame, error) {
		slog.Error("failed to select game", "trace", trace, "err", err)
		return OthelloGame{}, fmt.Errorf("failed select game: %v", err)
	}

	rows, err := q.QueryContext(ctx, "SELECT id, board, moves, white_id, black_id, white_name, black_name FROM games WHERE white_id = $1 OR black_id = $1;", playerID)
	if err != nil {
		return fail(err)
	}
	defer rows.Close()

	if !rows.Next() {
		return OthelloGame{}, ErrGameNotFound
	}
	var row GameRow
	if err := rows.Scan(&row.ID, &row.boardStr, &row.moveListStr, &row.whiteID, &row.blackID, &row.whiteName, &row.blackName); err != nil {
		return fail(err)
	}
	game, err := mapGameRow(row)
	if err != nil {
		return fail(err)
	}

	slog.Info("selected game", "trace", trace, "game", game, "playerID", playerID)
	return game, nil
}

type GameRow struct {
	ID          string
	boardStr    string
	moveListStr string
	whiteID     string
	blackID     string
	whiteName   string
	blackName   string
}

func scanGameList(rows *sql.Rows) ([]OthelloGame, error) {
	var gameList []OthelloGame
	for rows.Next() {
		var row GameRow
		if err := rows.Scan(&row.ID, &row.boardStr, &row.moveListStr, &row.whiteID, &row.blackID, &row.whiteName, &row.blackName); err != nil {
			return nil, fmt.Errorf("failed to scan game: %v", err)
		}
		game, err := mapGameRow(row)
		if err != nil {
			return nil, fmt.Errorf("failed to map game row: %v", err)
		}
		gameList = append(gameList, game)
	}
	return gameList, nil
}

func mapGameRow(row GameRow) (OthelloGame, error) {
	fail := func(err error) (OthelloGame, error) {
		return OthelloGame{}, fmt.Errorf("failed to map game row: %v", err)
	}
	game := OthelloGame{ID: row.ID, WhitePlayer: MakePlayer(row.whiteID, row.whiteName), BlackPlayer: MakePlayer(row.blackID, row.blackName)}
	if err := game.Board.UnmarshalString(row.boardStr); err != nil {
		return fail(err)
	}
	moveList, err := UnmarshalMoveList(row.moveListStr)
	if err != nil {
		return fail(err)
	}
	game.MoveList = moveList
	return game, nil
}

func CheckGameParticipation(ctx context.Context, q Query, player1Id string, player2Id *string) error {
	trace := ctx.Value("trace")

	row, err := q.QueryContext(ctx, "SELECT COUNT(*) FROM games WHERE white_id = $1 OR black_id = $1 OR white_id = $2 OR black_id = $2;", player1Id, player2Id)
	if err != nil {
		slog.Error("failed to get game", "trace", trace, "err", err)
		return err
	}
	defer row.Close()

	if !row.Next() {
		panic("assertion error: a count select query should return at least one record")
	}
	var count int
	if err = row.Scan(&count); err != nil {
		return err
	}
	if count > 0 {
		return ErrAlreadyPlaying
	}
	return nil
}

func SetGame(ctx context.Context, q Query, game OthelloGame, expireTime time.Time) error {
	trace := ctx.Value("trace")

	boardStr := game.Board.MarshalString()
	moveListStr := MarshalMoveList(game.MoveList)

	_, err := q.ExecContext(ctx,
		"INSERT OR REPLACE INTO games (id, board, white_id, black_id, white_name, black_name, moves, expire_time) VALUES ($1, $2, $3, $4, $5, $6, $7, $8);",
		game.ID,
		boardStr,
		game.WhitePlayer.ID,
		game.BlackPlayer.ID,
		game.WhitePlayer.Name,
		game.BlackPlayer.Name,
		moveListStr,
		expireTime)
	if err != nil {
		slog.Error("failed to insert or replace games", "trace", trace, "err", err)
		return err
	}

	return nil
}

func DeleteGame(ctx context.Context, q Query, game OthelloGame) error {
	trace := ctx.Value("trace")

	_, err := q.ExecContext(ctx, "DELETE FROM games WHERE white_id = $1 AND black_id = $2;", game.WhitePlayer.ID, game.BlackPlayer.ID)
	if err != nil {
		slog.Error("failed to delete game", "trace", trace, "err", err)
		return err
	}
	return nil
}

func CountGames(db *sql.DB) (int, error) {
	row, err := db.Query("SELECT COUNT(*) FROM games;")
	if err != nil {
		return 0, fmt.Errorf("failed to count games: %s", err)
	}
	defer row.Close()

	if !row.Next() {
		panic("assertion error: a count select query should return at least one record")
	}
	var count int
	if err = row.Scan(&count); err != nil {
		return 0, fmt.Errorf("failed to scan count: %vw", err)
	}
	return count, nil
}

var ErrAlreadyPlaying = errors.New("one or more players are already in a game")

func GameExpireTime() time.Time {
	return time.Now().Add(GameStoreTtl)
}

func CreateGame(ctx context.Context, db *sql.DB, blackPlayer Player, whitePlayer Player) (OthelloGame, error) {
	trace := ctx.Value(TraceKey)

	fail := func(err error) (OthelloGame, error) {
		slog.Error("failed to create game", "trace", trace, "err", err)
		return OthelloGame{}, err
	}

	id, err := uuid.NewUUID()
	if err != nil {
		return fail(err)
	}
	game := OthelloGame{ID: id.String(), WhitePlayer: whitePlayer, BlackPlayer: blackPlayer, Board: InitialBoard()}

	tx, err := db.BeginTx(ctx, &sql.TxOptions{Isolation: sql.LevelSerializable})
	if err != nil {
		return fail(fmt.Errorf("failed to open create game tx: %s", err))
	}
	defer tx.Rollback()

	var player2Id *string
	if whitePlayer.IsHuman() {
		player2Id = &whitePlayer.ID
	}

	err = CheckGameParticipation(ctx, tx, blackPlayer.ID, player2Id)
	if err != nil {
		return fail(err)
	}
	if err := SetGame(ctx, tx, game, GameExpireTime()); err != nil {
		return fail(err)
	}

	if err := tx.Commit(); err != nil {
		return fail(fmt.Errorf("failed to commit create game tx: %s", err))
	}

	slog.Info("created and inserted game", "trace", trace, "game", game)
	return game, nil
}

func CreateBotGame(ctx context.Context, db *sql.DB, blackPlayer Player, level uint64) (OthelloGame, error) {
	return CreateGame(ctx, db, blackPlayer, MakeBotPlayer(level))
}

var ErrTurn = errors.New("not players turn")
var ErrInvalidMove = errors.New("invalid move")

func MakeMove(ctx context.Context, q Query, game OthelloGame, move Tile) (OthelloGame, error) {
	game.MakeMove(move)
	game.TrySkipTurn()

	if len(game.Board.FindCurrentMoves()) == 0 {
		err := DeleteGame(ctx, q, game)
		return game, err
	}
	if err := SetGame(ctx, q, game, GameExpireTime()); err != nil {
		return OthelloGame{}, err
	}

	return game, nil
}

func MakeMoveValidated(ctx context.Context, db *sql.DB, playerID string, move Tile) (OthelloGame, error) {
	trace := ctx.Value(TraceKey)
	fail := func(err error) (OthelloGame, error) {
		slog.Error("failed to make move", "trace", trace, "err", err)
		return OthelloGame{}, err
	}

	tx, err := db.BeginTx(ctx, &sql.TxOptions{Isolation: sql.LevelSerializable})
	if err != nil {
		return fail(fmt.Errorf("failed to open make move tx: %s", err))
	}
	defer tx.Rollback()

	game, err := GetGame(ctx, tx, playerID)
	if err != nil {
		return fail(err)
	}

	if game.CurrentPlayer().ID != playerID {
		return OthelloGame{}, ErrTurn
	}
	if !slices.Contains(game.Board.FindCurrentMoves(), move) {
		return OthelloGame{}, ErrInvalidMove
	}

	game, err = MakeMove(ctx, tx, game, move)
	if err != nil {
		return fail(err)
	}
	if err := tx.Commit(); err != nil {
		return fail(fmt.Errorf("failed to commit make move tx: %s", err))
	}

	return game, nil
}

func ExpireGamesCron(db *sql.DB) {
	trace := "expire-games-task"
	ctx := context.WithValue(context.Background(), TraceKey, trace)

	ticker := time.NewTicker(time.Minute)
	defer ticker.Stop()

	for range ticker.C {
		if err := ExpireGames(ctx, db); err != nil {
			slog.Error("failed to expire games", "trace", trace, "err", err)
		}
	}
}

func ExpireGames(ctx context.Context, db *sql.DB) error {
	trace := ctx.Value(TraceKey)

	tx, err := db.BeginTx(ctx, &sql.TxOptions{Isolation: sql.LevelSerializable})
	if err != nil {
		return fmt.Errorf("failed to open expire games transaction: %s", err)
	}
	defer tx.Rollback()

	t := time.Now()

	rows, err := tx.QueryContext(ctx, "SELECT id, board, moves, white_id, black_id, white_name, black_name FROM games WHERE expire_time < $1;", t)
	if err != nil {
		return fmt.Errorf("failed to select expired games: %s", err)
	}
	defer rows.Close()

	gameList, err := scanGameList(rows)
	if err != nil {
		return err
	}

	_, err = tx.ExecContext(ctx, "DELETE FROM games WHERE expire_time < $1;", t)
	if err != nil {
		return fmt.Errorf("failed to delete expire games: %s", err)
	}

	if err := tx.Commit(); err != nil {
		return fmt.Errorf("failed to commit expire games tx: %s", err)
	}

	for _, game := range gameList {
		statsResult, err := UpdateStats(ctx, db, GameResult{Winner: game.CurrentPlayer(), Loser: game.CurrentPlayer(), IsDraw: false})
		if err != nil {
			return fmt.Errorf("failed to update stats for expired Game: %s", err)
		}
		slog.Info("updated stats result", "trace", trace, "result", statsResult)
	}

	return nil
}
