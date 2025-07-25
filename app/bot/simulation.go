package bot

import (
	"context"
	"github.com/bwmarrin/discordgo"
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

type SimStore = ttlcache.Cache[string, *SimState]

func NewSimStore() *SimStore {
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

const SimCount = othello.BoardSize * othello.BoardSize // maximum number of possible simulation states

// Simulation Perform the simulation and write the results to a response channel as fast as possible
// Caller decides the pace to receive the messages
func (h Handler) Simulation(ctx context.Context, initialGame Game, simChan chan SimMsg) {
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

		request := EngineRequest{
			Board:    game.Board,
			Depth:    LevelToDepth(GetBotLevel(game.CurrentPlayer().ID)),
			T:        GetMoveRequest,
			RespChan: make(chan []othello.RankTile, 1),
		}
		h.Eq <- request

		moves := <-request.RespChan

		if len(moves) > 0 {
			move = moves[0].Tile
			slog.Info("completed simulation iteration", "index", i, "trace", trace, "move", move)

			game.MakeMove(move)
			game.TrySkipTurn()
			game.CurrPotentialMoves = nil

			embed := CreateGameEmbed(game)
			img := othello.DrawBoardMoves(h.Rc, game.Board, game.LoadPotentialMoves())

			simChan <- SimMsg{img: img, embed: embed}
		} else {
			slog.Info("finished simulation", "trace", trace, "moves", moves, "move", move)

			embed := CreateSimulationEmbed(game, move)
			img := othello.DrawBoardMoves(h.Rc, game.Board, game.LoadPotentialMoves())

			simChan <- SimMsg{img: img, embed: embed}
			close(simChan)
			return
		}
	}
}

type SimContext struct {
	Ctx      context.Context
	Cancel   func()
	State    *SimState
	RecvChan chan SimMsg
}

// ReceiveSimulate Receive simulation results and write to discord directly
// Handles events such as simulation messages, stop signals, pause State, and timeouts
func ReceiveSimulate(ctx SimContext, dg *discordgo.Session, i *discordgo.InteractionCreate, delay time.Duration) {
	trace := ctx.Ctx.Value("trace")

	ticker := time.NewTicker(delay)
	for index := 0; ; index++ {
		select {
		case <-ticker.C:
			if ctx.State.IsPaused.Load() {
				continue
			}
			sim, ok := <-ctx.RecvChan
			if !ok {
				slog.Info("simulation receiver complete", "trace", trace)
				return
			}
			slog.Info("received simulated embed", "trace", trace, "index", index)

			if _, err := dg.InteractionResponseEdit(i.Interaction, createEmbedEdit(sim.embed, sim.img)); err != nil {
				slog.Error("failed to edit message simulate", "err", err)
			}
		case <-ctx.State.StopChan:
			ctx.Cancel()
			slog.Info("simulation receiver stopped", "trace", trace)
			return
		case <-ctx.Ctx.Done():
			slog.Info("simulation receiver timed out", "trace", trace, "err", ctx.Ctx.Err())
			return
		}
	}
}
