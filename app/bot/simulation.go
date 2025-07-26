package bot

import (
	"context"
	"github.com/jellydator/ttlcache/v3"
	"go.uber.org/atomic"
	"log/slog"
	"othellocord/app/othello"
	"time"
)

const SimulationTtl = time.Hour

type SimState struct {
	StopChan chan struct{}
	IsPaused atomic.Bool
}

type SimCache = ttlcache.Cache[string, *SimState]

func NewSimCache() *SimCache {
	cache := ttlcache.New[string, *SimState]()
	cache.OnEviction(func(_ context.Context, _ ttlcache.EvictionReason, item *ttlcache.Item[string, *SimState]) {
		signals := item.Value()
		if signals.StopChan != nil {
			signals.StopChan <- struct{}{}
		}
		slog.Info("stopped simulation", "key", item.Key())
	})
	return cache
}

type SimMsg struct {
	Game     Game
	Move     othello.Tile
	Finished bool
}

const SimCount = othello.BoardSize * othello.BoardSize // maximum number of possible simulation states

func Simulation(ctx context.Context, wq chan WorkerRequest, initialGame Game, simChan chan SimMsg) {
	trace := ctx.Value("trace")

	var game = initialGame
	var move othello.Tile

	for i := 0; ; i++ {
		select {
		case <-ctx.Done():
			slog.Info("cancelled simulation", "index", i, "trace", trace, "move", move)
			return
		default:
		}

		depth := LevelToDepth(GetBotLevel(game.CurrentPlayer().ID))
		request := WorkerRequest{
			Board:    game.Board,
			Depth:    depth,
			T:        GetMoveRequest,
			RespChan: make(chan []othello.RankTile, 1),
		}
		wq <- request

		moves := <-request.RespChan

		if len(moves) > 1 {
			panic("expected exactly engine to no more than one moves") // we only requested one move
		}
		if len(moves) == 1 {
			move = moves[0].Tile

			game.MakeMove(move)
			game.TrySkipTurn()
			game.CurrPotentialMoves = nil

			simChan <- SimMsg{Game: game, Move: move}
		} else {
			slog.Info("finished simulation", "trace", trace, "moves", moves, "move", move)

			simChan <- SimMsg{Game: game, Move: move, Finished: true}
			close(simChan)
			return
		}
	}
}

type SimContext struct {
	Ctx    context.Context
	Cancel func()
	State  *SimState
	Rc     othello.RenderCache
}

func ReceiveSimulate(simCtx SimContext, recvChan chan SimMsg, handleSend func(SimMsg), handleCancel func(), delay time.Duration) {
	trace := simCtx.Ctx.Value("trace")

	ticker := time.NewTicker(delay)
	for index := 0; ; index++ {
		select {
		case <-ticker.C:
			if simCtx.State.IsPaused.Load() {
				continue
			}
			msg, ok := <-recvChan
			if !ok {
				slog.Info("simulation receiver complete", "trace", trace)
				return
			}
			handleSend(msg)
		case <-simCtx.State.StopChan:
			simCtx.Cancel()
			handleCancel()
			slog.Info("simulation receiver stopped", "trace", trace)
			return
		case <-simCtx.Ctx.Done():
			handleCancel()
			slog.Info("simulation receiver timed out", "trace", trace, "err", simCtx.Ctx.Err())
			return
		}
	}
}
