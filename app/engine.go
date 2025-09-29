package app

import (
	"bufio"
	"errors"
	"fmt"
	"log"
	"log/slog"
	"os/exec"
	"slices"
	"strings"
	"time"
)

type MoveRequestKind int

const (
	BestMoveKind = iota
	RankMovesKind
)

type MoveReq struct {
	Kind   MoveRequestKind
	Game   OthelloGame
	Depth  uint64
	RespCh chan MoveResp
}

type MoveResp struct {
	Moves []RankTile
	Err   error
}

type NTestShell struct {
	stdout    *bufio.Scanner
	stdin     *bufio.Writer
	moveReqCh chan MoveReq
}

var ErrEmptyPath = errors.New("path argument should not be empty")

func StartNTestShell(path string) (*NTestShell, error) {
	if path == "" {
		return nil, ErrEmptyPath
	}
	cmd := exec.Command(path)

	stdout, err := cmd.StdoutPipe()
	if err != nil {
		return nil, fmt.Errorf("failed to open stdout pipe to ntest: %v", err)
	}
	stdin, err := cmd.StdinPipe()
	if err != nil {
		return nil, fmt.Errorf("failed to open stdin pipe to ntest: %v", err)
	}

	sh := &NTestShell{stdout: bufio.NewScanner(stdout), stdin: bufio.NewWriter(stdin), moveReqCh: make(chan MoveReq)}

	if err := cmd.Start(); err != nil {
		return nil, fmt.Errorf("failed to start ntest: %v", err)
	}

	var startLines = []string{
		"Ntest version as of Dec 31 2004",
		"Copyright (c) Chris Welty",
		"All Rights Reserved",
		"",
	}
	for _, line := range startLines {
		if err := sh.expect(line); err != nil {
			return nil, err
		}
	}
	return sh, nil
}

func (sh *NTestShell) stdinWrite(cmd string) error {
	slog.Info("writing cmd to stdin", "cmd", cmd)
	if _, err := sh.stdin.WriteString(cmd); err != nil {
		return fmt.Errorf("failed to stdinWrite to ntest stdin: %v", err)
	}
	if err := sh.stdin.Flush(); err != nil {
		return fmt.Errorf("failed to flush ntest stdin: %v", err)
	}
	return nil
}

func (sh *NTestShell) stdoutText() string {
	line := sh.stdout.Text()
	if line != "" {
		slog.Info("ntest stdout", "line", line)
	}
	return line
}

func (sh *NTestShell) expect(expected string) error {
	if sh.stdout.Scan() {
		line := sh.stdoutText()
		if line != expected {
			return fmt.Errorf("expected: %s from ntest stdout, got: %s", expected, line)
		}
	}
	if err := sh.stdout.Err(); err != nil {
		return err
	}
	return nil
}

func (sh *NTestShell) depthCmd(depth uint64) error {
	if err := sh.stdinWrite(fmt.Sprintf("set depth %d\n", depth)); err != nil {
		return err
	}

	for sh.stdout.Scan() {
		line := sh.stdoutText()
		if strings.Contains(line, "set myname") {
			break
		}
	}
	if err := sh.stdout.Err(); err != nil {
		return err
	}

	return nil
}

func (sh *NTestShell) setGameCmd(game OthelloGame) error {
	return sh.stdinWrite(fmt.Sprintf("set game %s\n", game.MarshalGGF()))
}

var ErrInvalidGameState = errors.New("game state GGF format is invalid")

func (sh *NTestShell) goCmd() (RankTile, error) {
	if err := sh.stdinWrite("go\n"); err != nil {
		return RankTile{}, err
	}

	var target string
	const head = "=== "

	for sh.stdout.Scan() {
		line := sh.stdoutText()
		if strings.Contains(line, head) {
			target = strings.TrimPrefix(line, head)
			break
		}
	}
	if err := sh.stdout.Err(); err != nil {
		return RankTile{}, err
	}

	if strings.Contains(target, "PA") {
		return RankTile{}, ErrInvalidGameState
	}

	tokens := strings.Split(target, "/")
	if len(tokens) < 1 {
		return RankTile{}, fmt.Errorf("expected line to contain at least 1 token, got: %s", target)
	}
	strH := ""
	if len(tokens) >= 2 {
		strH = tokens[1]
	}
	return ParseRankTile(tokens[0], strH)
}

func (sh *NTestShell) hintCmd() ([]RankTile, []error) {
	if err := sh.stdinWrite("hint 64\n"); err != nil {
		return nil, []error{err}
	}

	type Pair struct {
		tile RankTile
		set  bool
	}

	var tiles []RankTile
	var errs []error
	var tileMap [BoardSize][BoardSize]Pair

	for sh.stdout.Scan() {
		line := sh.stdoutText()
		if line == "status" {
			break
		}
		if strings.HasPrefix(line, "search") || strings.HasPrefix(line, "book") {
			tokens := strings.Fields(line)
			if len(tokens) < 3 {
				errs = append(errs, fmt.Errorf("expected line to contain at least 3 token, got: %s", line))
				continue
			}
			tile, err := ParseRankTile(tokens[1], tokens[2])
			if err == nil {
				tileMap[tile.Tile.Row][tile.Tile.Col] = Pair{set: true, tile: tile}
			} else {
				errs = append(errs, err)
			}
		}
	}
	if err := sh.stdout.Err(); err != nil {
		errs = append(errs, err)
	}

	for row := 0; row < BoardSize; row++ {
		for col := 0; col < BoardSize; col++ {
			pair := tileMap[row][col]
			if pair.set {
				tiles = append(tiles, pair.tile)
			}
		}
	}

	return tiles, errs
}

var ErrNoMoves = errors.New("no moves for game")

func (sh *NTestShell) findBestMove(game OthelloGame, depth uint64) (RankTile, error) {
	moves := game.Board.FindCurrentMoves()
	if len(moves) == 0 {
		return RankTile{}, ErrNoMoves
	}

	var tile RankTile
	var err error

	if err = sh.depthCmd(depth); err != nil {
		return RankTile{}, err
	}
	if err = sh.setGameCmd(game); err != nil {
		return RankTile{}, err
	}
	if tile, err = sh.goCmd(); err != nil {
		return RankTile{}, err
	}

	slog.Info("found best tile", "depth", depth, "move", tile)
	return tile, err
}

func (sh *NTestShell) findRankedMoves(game OthelloGame, depth uint64) ([]RankTile, error) {
	if err := sh.depthCmd(depth); err != nil {
		return nil, err
	}
	if err := sh.setGameCmd(game); err != nil {
		return nil, err
	}

	var tiles []RankTile
	var errs []error

	if tiles, errs = sh.hintCmd(); len(errs) > 0 {
		return nil, errors.Join(errs...)
	}

	slog.Info("found ranked tiles", "depth", depth, "Moves", tiles)
	return tiles, nil
}

func (sh *NTestShell) ListenRequests() {
	for req := range sh.moveReqCh {
		start := time.Now()
		switch req.Kind {
		case BestMoveKind:
			move, err := sh.findBestMove(req.Game, req.Depth)
			if err != nil {
				slog.Error("failed to find best tile", "err", err)
			}
			req.RespCh <- MoveResp{Moves: []RankTile{move}, Err: err}
		case RankMovesKind:
			moves, err := sh.findRankedMoves(req.Game, req.Depth)
			if err != nil {
				slog.Error("failed to find ranked tiles", "err", err)
			}
			req.RespCh <- MoveResp{Moves: moves, Err: err}
		default:
			log.Fatalf("invalid move request Kind: %d", req.Kind)
		}
		slog.Info("move request complete", "duration", start.Sub(time.Now()))
	}
}

func (sh *NTestShell) FindBestMove(game OthelloGame, depth uint64) chan MoveResp {
	ch := make(chan MoveResp, 1)
	sh.moveReqCh <- MoveReq{Kind: BestMoveKind, Game: game, Depth: depth, RespCh: ch}
	return ch
}

func (sh *NTestShell) FindRankedMoves(game OthelloGame, depth uint64) chan MoveResp {
	ch := make(chan MoveResp, 1)
	sh.moveReqCh <- MoveReq{Kind: RankMovesKind, Game: game, Depth: depth, RespCh: ch}
	return ch
}

func (resp MoveResp) assertValidMove(game OthelloGame) RankTile {
	if len(resp.Moves) == 0 {
		log.Fatalf("engine produced no moves for best move request for game: %s", game.MarshalGGF())
	}
	move := resp.Moves[0]
	if !slices.Contains(game.Board.FindCurrentMoves(), move.Tile) {
		log.Fatalf("engine produced an illegal tile: %s for game: %s", move.Tile, game.MarshalGGF())
	}
	return move
}

func (resp MoveResp) assertValidMoves(game OthelloGame) {
	var tileMap [BoardSize][BoardSize]bool
	for _, tile := range resp.Moves {
		tileMap[tile.Row][tile.Col] = true
	}
	for _, tile := range game.Board.FindCurrentMoves() {
		if !tileMap[tile.Row][tile.Col] {
			log.Fatalf("engine produced illegal tiles: %s for game: %s", resp.Moves, game.MarshalGGF())
		}
	}
}
