package app

import (
	"bufio"
	"context"
	"errors"
	"fmt"
	"log/slog"
	"os/exec"
	"strings"
	"time"

	"go.uber.org/atomic"
	"golang.org/x/exp/slices"
)

type MoveRequestKind int

const (
	BestMoveKind = iota
	RankMovesKind
)

type moveReq struct {
	Kind        MoveRequestKind
	Game        OthelloGame
	Depth       uint64
	RespCh      chan moveResp
	IsCancelled atomic.Bool
	Trace       any
}

type moveResp struct {
	MoveResult
	Err error
}

type MoveResult struct {
	Move  RankTile
	Moves []RankTile
}

type NTestShell struct {
	name      string
	stdout    *bufio.Scanner
	stdin     *bufio.Writer
	moveReqCh chan moveReq
}

var ErrEmptyPath = errors.New("path argument should not be empty")

func StartNTestShell(name string, path string, moveReqCh chan moveReq) (*NTestShell, error) {
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

	sh := &NTestShell{name: name, stdout: bufio.NewScanner(stdout), stdin: bufio.NewWriter(stdin), moveReqCh: moveReqCh}

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

func (sh *NTestShell) write(cmd string) error {
	slog.Info("writing cmd to stdin", "cmd", cmd)
	if _, err := sh.stdin.WriteString(cmd); err != nil {
		return fmt.Errorf("failed to write to ntest stdin: %v", err)
	}
	if err := sh.stdin.Flush(); err != nil {
		return fmt.Errorf("failed to flush ntest stdin: %v", err)
	}
	return nil
}

func (sh *NTestShell) stdoutText() string {
	line := sh.stdout.Text()
	if line != "" {
		slog.Info("ntest stdout", "line", line, "shellName", sh.name)
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
	return sh.stdout.Err()
}

func (sh *NTestShell) depthCmd(depth uint64) error {
	if err := sh.write(fmt.Sprintf("set depth %d\n", depth)); err != nil {
		return err
	}

	for sh.stdout.Scan() {
		line := sh.stdoutText()
		if strings.Contains(line, "set myname") {
			break
		}
	}
	return sh.stdout.Err()
}

func (sh *NTestShell) setGameCmd(game OthelloGame) error {
	return sh.write(fmt.Sprintf("set game %s\n", game.MarshalGGF()))
}

var ErrInvalidGameState = errors.New("game state GGF format is invalid")

func (sh *NTestShell) goCmd() (RankTile, error) {
	if err := sh.write("go\n"); err != nil {
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
	if err := sh.write("hint 64\n"); err != nil {
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

	for row := range BoardSize {
		for col := range BoardSize {
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

	if len(moves) == 0 {
		return RankTile{}, fmt.Errorf("engine produced no moves for best move request for game: %s", game.MarshalGGF())
	}
	move := moves[0]
	if !slices.Contains(game.Board.FindCurrentMoves(), move) {
		return RankTile{}, fmt.Errorf("engine produced an illegal move: %s for game: %s", move, game.MarshalGGF())
	}
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

	actualTiles := game.Board.FindCurrentMoves()

	failRules := func() ([]RankTile, error) {
		return nil, fmt.Errorf("engine produced illegal moves=%v for game=%s, expected=%s", tiles, game.MarshalGGF(), actualTiles)
	}

	if len(actualTiles) != len(tiles) {
		return failRules()
	}
	var tileMap [BoardSize][BoardSize]bool
	for _, tile := range tiles {
		tileMap[tile.Row][tile.Col] = true
	}
	for _, tile := range actualTiles {
		if !tileMap[tile.Row][tile.Col] {
			return failRules()
		}
	}

	slices.SortFunc(tiles, func(tile1 RankTile, tile2 RankTile) int {
		return int(tile2.H - tile1.H)
	})

	return tiles, nil
}

func (sh *NTestShell) ListenRequests() {
	for req := range sh.moveReqCh {
		trace := req.Trace
		start := time.Now()

		if req.IsCancelled.Load() {
			slog.Warn("skipping cancelled move request", "trace", trace, "shellName", sh.name)
			continue
		}

		slog.Info("move request begin", "req", req, "trace", trace, "shellName", sh.name)

		var resp moveResp
		switch req.Kind {
		case BestMoveKind:
			move, err := sh.findBestMove(req.Game, req.Depth)
			if err != nil {
				slog.Error("failed to find best tile", "trace", trace, "err", err)
			}
			resp = moveResp{MoveResult: MoveResult{Move: move}, Err: err}
		case RankMovesKind:
			moves, err := sh.findRankedMoves(req.Game, req.Depth)
			if err != nil {
				slog.Error("failed to find ranked tiles", "trace", trace, "err", err)
			}
			resp = moveResp{MoveResult: MoveResult{Moves: moves}, Err: err}
		default:
			panic(fmt.Sprintf("invalid move request kind: %d", req.Kind))
		}

		slog.Info("move request complete",
			"req", req, "resp", resp, "shellName", sh.name, "trace", trace, "duration", time.Since(start))
		req.RespCh <- resp
	}
}

type NTestShellPool struct {
	moveReqCh chan moveReq
}

func MakeShellPool(path string, shellCount int) (*NTestShellPool, error) {
	moveReqCh := make(chan moveReq)
	pool := &NTestShellPool{moveReqCh: moveReqCh}

	var shells []*NTestShell

	for i := range shellCount {
		sh, err := StartNTestShell(fmt.Sprintf("node-%d", i), path, moveReqCh)
		if err != nil {
			return nil, fmt.Errorf("starting ntest shell: %w", err)
		}
		shells = append(shells, sh)
	}
	for _, sh := range shells {
		go sh.ListenRequests()
	}

	return pool, nil
}

func (sh *NTestShellPool) sendRequest(ctx context.Context, req moveReq) (MoveResult, error) {
	req.RespCh = make(chan moveResp, 1)

	sh.moveReqCh <- req

	select {
	case resp := <-req.RespCh:
		if resp.Err != nil {
			return MoveResult{}, resp.Err
		} else {
			return resp.MoveResult, nil
		}
	case <-ctx.Done():
		// marks a request as canceled, so if it is in the queue, it will be ignored once handled. does not cancel inflight requests (we cannot).
		req.IsCancelled.Store(true)
		return MoveResult{}, ctx.Err()
	}
}

func (sh *NTestShellPool) FindBestMove(ctx context.Context, game OthelloGame, depth uint64) (MoveResult, error) {
	return sh.sendRequest(ctx, moveReq{Kind: BestMoveKind, Game: game, Depth: depth, Trace: ctx.Value(TraceKey)})
}

func (sh *NTestShellPool) FindRankedMoves(ctx context.Context, game OthelloGame, depth uint64) (MoveResult, error) {
	return sh.sendRequest(ctx, moveReq{Kind: RankMovesKind, Game: game, Depth: depth, Trace: ctx.Value(TraceKey)})
}
