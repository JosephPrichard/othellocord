package app

import (
	"context"
	"database/sql"
	"errors"
	"fmt"
	"log/slog"
	"time"
)

type OthelloGame struct {
	Board              OthelloBoard
	WhitePlayer        Player
	BlackPlayer        Player
	MoveList           []Tile
	CurrPotentialMoves []Tile // do not mutate this directly, instead call LoadPotentialMoves which will reassign
}

func (o *OthelloGame) MakeMove(move Tile) {
	o.Board.MakeMove(move)
	o.MoveList = append(o.MoveList, move)
}

func (o *OthelloGame) LoadPotentialMoves() []Tile {
	if o.CurrPotentialMoves == nil {
		o.CurrPotentialMoves = o.Board.FindCurrentMoves()
	}
	return o.CurrPotentialMoves
}

func (o *OthelloGame) TrySkipTurn() {
	if len(o.LoadPotentialMoves()) == 0 {
		o.Board.IsBlackMove = !o.Board.IsBlackMove
		o.CurrPotentialMoves = nil
	}
}

func (o *OthelloGame) IsGameOver() bool {
	return len(o.LoadPotentialMoves()) == 0
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

func GetGame(ctx context.Context, q QueryExecutor, playerID string) (OthelloGame, error) {
	trace := ctx.Value(TraceKey)

	rows, err := q.Query("SELECT board, moves, white_id, black_id, white_name, black_name FROM games WHERE white_id = $1 OR black_id = $1;", playerID)
	if err != nil {
		slog.Error("failed to get game", "trace", trace, "err", err)
		return OthelloGame{}, err
	}
	defer func() {
		_ = rows.Close()
	}()

	if !rows.Next() {
		return OthelloGame{}, ErrGameNotFound
	}

	game, err := scanGame(rows)
	if err != nil {
		return OthelloGame{}, err
	}

	slog.Info("selected game", "trace", trace, "game", game, "playerID", playerID)
	return game, nil
}

type GameRow struct {
	boardStr    string
	moveListStr string
	whiteID     string
	blackID     string
	whiteName   string
	blackName   string
}

func scanGame(rows *sql.Rows) (OthelloGame, error) {
	var row GameRow
	if err := rows.Scan(&row.boardStr, &row.moveListStr, &row.whiteID, &row.blackID, &row.whiteName, &row.blackName); err != nil {
		return OthelloGame{}, err
	}

	var game OthelloGame
	game.WhitePlayer = MakePlayer(row.whiteID, row.whiteName)
	game.BlackPlayer = MakePlayer(row.blackID, row.blackName)

	if err := game.Board.UnmarshalString(row.boardStr); err != nil {
		return OthelloGame{}, err
	}
	moveList, err := UnmarshalMoveList(row.moveListStr)
	if err != nil {
		return OthelloGame{}, err
	}
	game.MoveList = moveList

	return game, nil
}

func CheckGameParticipation(ctx context.Context, q QueryExecutor, player1Id string, player2Id *string) error {
	trace := ctx.Value("trace")

	row, err := q.Query("SELECT COUNT(*) FROM games WHERE white_id = $1 OR black_id = $1 OR white_id = $2 OR black_id = $2;", player1Id, player2Id)
	if err != nil {
		slog.Error("failed to get game", "trace", trace, "err", err)
		return err
	}
	defer func() {
		_ = row.Close()
	}()

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

func SetGame(ctx context.Context, q QueryExecutor, game OthelloGame) error {
	trace := ctx.Value("trace")

	boardStr := game.Board.MarshalString()
	moveListStr := MarshalMoveList(game.MoveList)
	expireTime := time.Now().Add(GameStoreTtl)

	_, err := q.Exec(
		"INSERT OR REPLACE INTO games (board, white_id, black_id, white_name, black_name, moves, expire_time) VALUES ($1, $2, $3, $4, $5, $6, $7);",
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

func DeleteGame(ctx context.Context, q QueryExecutor, game OthelloGame) error {
	trace := ctx.Value("trace")

	_, err := q.Exec("DELETE FROM games WHERE white_id = $1 AND black_id = $2;", game.WhitePlayer.ID, game.BlackPlayer.ID)
	if err != nil {
		slog.Error("failed to delete game", "trace", trace, "err", err)
		return err
	}
	return nil
}

var ErrAlreadyPlaying = errors.New("one or more players are already in a game")

func CreateGame(ctx context.Context, db *sql.DB, blackPlayer Player, whitePlayer Player) (OthelloGame, error) {
	trace := ctx.Value(TraceKey)

	game := OthelloGame{WhitePlayer: whitePlayer, BlackPlayer: blackPlayer, Board: InitialBoard()}

	tx, err := db.BeginTx(ctx, &sql.TxOptions{Isolation: sql.LevelSerializable})
	if err != nil {
		slog.Error("failed to open create game tx", "trace", trace, "err", err)
		return OthelloGame{}, err
	}
	defer func() {
		_ = tx.Rollback()
	}()

	var player2Id *string
	if whitePlayer.IsHuman() {
		player2Id = &whitePlayer.ID
	}

	err = CheckGameParticipation(ctx, tx, blackPlayer.ID, player2Id)
	if err != nil {
		return OthelloGame{}, err
	}
	if err := SetGame(ctx, tx, game); err != nil {
		return OthelloGame{}, err
	}

	if err := tx.Commit(); err != nil {
		slog.Error("failed to commit create game tx", "trace", trace, "err", err)
		return OthelloGame{}, err
	}

	slog.Info("created and inserted game", "trace", trace, "game", game)
	return game, nil
}

func CreateBotGame(ctx context.Context, db *sql.DB, blackPlayer Player, level int) (OthelloGame, error) {
	return CreateGame(ctx, db, blackPlayer, MakeBotPlayer(level))
}

var ErrTurn = errors.New("not players turn")
var ErrInvalidMove = errors.New("invalid move")

func MakeMove(ctx context.Context, q QueryExecutor, game OthelloGame, move Tile) (OthelloGame, error) {
	game.MakeMove(move)
	game.CurrPotentialMoves = nil
	game.TrySkipTurn()

	if len(game.LoadPotentialMoves()) == 0 {
		if err := DeleteGame(ctx, q, game); err != nil {
			return OthelloGame{}, err
		}
		return game, nil
	}
	if err := SetGame(ctx, q, game); err != nil {
		return OthelloGame{}, err
	}

	return game, nil
}

func MakeMoveValidated(ctx context.Context, db *sql.DB, playerID string, move Tile) (OthelloGame, error) {
	trace := ctx.Value(TraceKey)

	tx, err := db.BeginTx(ctx, &sql.TxOptions{Isolation: sql.LevelSerializable})
	if err != nil {
		slog.Error("failed to open make move game tx", "trace", trace, "err", err)
		return OthelloGame{}, err
	}
	defer func() {
		_ = tx.Rollback()
	}()

	game, err := GetGame(ctx, db, playerID)
	if err != nil {
		return OthelloGame{}, err
	}

	if game.CurrentPlayer().ID != playerID {
		return OthelloGame{}, ErrTurn
	}
	for _, m := range game.LoadPotentialMoves() {
		if m == move {
			return MakeMove(ctx, tx, game, move)
		}
	}
	return OthelloGame{}, ErrInvalidMove
}

func ExpireGamesCron(db *sql.DB) {
	trace := "expire-games-task"

	ticker := time.NewTicker(time.Minute)
	defer ticker.Stop()

	for range ticker.C {
		ctx := context.WithValue(context.Background(), TraceKey, trace)
		if err := ExpireGames(ctx, db); err != nil {
			slog.Error("failed to expire games", "trace", trace, "err", err)
		}
	}
}

func ExpireGames(ctx context.Context, db *sql.DB) error {
	trace := ctx.Value(TraceKey)

	tx, err := db.BeginTx(ctx, &sql.TxOptions{Isolation: sql.LevelSerializable})
	if err != nil {
		return fmt.Errorf("failed to open expire games transaction: %w", err)
	}
	defer func() {
		_ = tx.Rollback()
	}()

	rows, err := tx.Query("SELECT white_id, black_id, white_name, black_name FROM games WHERE expire_time < $1;", time.Now())
	if err != nil {
		return fmt.Errorf("failed to select expired games: %w", err)
	}
	defer func() {
		_ = rows.Close()
	}()

	var gameList []OthelloGame
	for rows.Next() {
		game, err := scanGame(rows)
		if err != nil {
			return fmt.Errorf("failed to scan game: %w", err)
		}
		gameList = append(gameList, game)
	}

	_, err = tx.Exec("DELETE FROM games WHERE expire_time < $1;")
	if err != nil {
		return fmt.Errorf("failed to delete expire games: %w", err)
	}

	if err := tx.Commit(); err != nil {
		return fmt.Errorf("failed to commit expire games tx: %w", err)
	}

	for _, game := range gameList {
		gameResult := GameResult{Winner: game.CurrentPlayer(), Loser: game.CurrentPlayer(), IsDraw: false}
		statsResult, err := UpdateStats(ctx, db, gameResult)
		if err != nil {
			return fmt.Errorf("failed to update stats for expired game: %w", err)
		}
		slog.Info("updated stats result", "trace", trace, "result", statsResult)
	}

	return nil
}
