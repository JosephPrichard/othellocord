package othello

import (
	"log/slog"
	"math"
	"sort"
	"time"
)

const (
	Inf      = math.MaxFloat32
	MinDepth = 5
)

var (
	Corners = [][2]int{
		{0, 0}, {0, 7}, {7, 0}, {7, 7},
	}

	XcSquares = [][2]int{
		{1, 1}, {1, 6}, {6, 1}, {6, 6},
		{0, 1}, {0, 6}, {7, 1}, {7, 6},
		{1, 0}, {1, 7}, {6, 0}, {6, 7},
	}
)

type StackFrame struct {
	Board    Board
	Depth    int
	Alpha    float64
	Beta     float64
	HashKey  uint64
	Children []Board
	Index    int
}

func (sf *StackFrame) NextBoard() Board {
	board := sf.Children[sf.Index]
	sf.Index++
	return board
}

func (sf *StackFrame) HasNext() bool {
	return sf.Index < len(sf.Children)
}

func (sf *StackFrame) HasChildren() bool {
	return sf.Children != nil
}

type Agent struct {
	maxTime      int64 // in milliseconds
	stopTime     time.Time
	nodesVisited int
	stack        []StackFrame
	table        TTable
}

func NewOthelloAgent() Agent {
	return NewOthelloAgentWithParams((1<<12)+1, 3000)
}

func NewOthelloAgentWithParams(ttSize int, maxTime int64) Agent {
	return Agent{
		table:   NewTTable(ttSize),
		maxTime: maxTime,
		stack:   []StackFrame{},
	}
}

func (a *Agent) FindBestMove(board Board, maxDepth int) (Move, bool) {
	startTime := time.Now()
	a.nodesVisited = 0
	a.stopTime = startTime.Add(time.Duration(a.maxTime))

	moves := board.FindCurrentMoves()

	var bestMove Move
	var isSet bool

	if board.IsBlackMove {
		bestMove.H = -Inf
	} else {
		bestMove.H = Inf
	}

	for _, move := range moves {
		child := board.MakeMoved(move)

		heuristic := a.evaluateLoop(child, maxDepth-1)
		if (board.IsBlackMove && heuristic > bestMove.H) || (!board.IsBlackMove && heuristic < bestMove.H) {
			bestMove.Tile = move
			bestMove.H = heuristic
			isSet = true
		}
	}

	timeTaken := time.Now().Sub(startTime)

	slog.Info("finished an analysis", "maxDepth", maxDepth, "nodesVisited", a.nodesVisited,
		"ttHits", a.table.Hits(), "ttMisses", a.table.Misses(), "timeTakenMs", timeTaken)

	a.table.Clear()

	return bestMove, isSet
}

func (a *Agent) FindRankedMoves(board Board, maxDepth int) []Move {
	startTime := time.Now()
	a.nodesVisited = 0
	a.stopTime = startTime.Add(time.Duration(a.maxTime))

	moves := board.FindCurrentMoves()
	rankedMoves := make([]Move, 0, len(moves))

	for _, move := range moves {
		child := board.MakeMoved(move)
		heuristic := a.EvaluateLoop(child, maxDepth-1)
		rankedMoves = append(rankedMoves, Move{Tile: move, H: heuristic})
	}
	a.table.Clear()

	timeTaken := time.Now().Sub(startTime)

	slog.Info("finished an analysis", "maxDepth", maxDepth, "nodesVisited", a.nodesVisited,
		"ttHits", a.table.Hits(), "ttMisses", a.table.Misses(), "timeTakenMs", timeTaken)

	if board.IsBlackMove {
		sort.Slice(rankedMoves, func(i, j int) bool {
			return rankedMoves[i].H > rankedMoves[j].H
		})
	} else {
		sort.Slice(rankedMoves, func(i, j int) bool {
			return rankedMoves[i].H < rankedMoves[j].H
		})
	}

	var duplicates [BoardSize][BoardSize]bool

	filtered := make([]Move, 0, len(rankedMoves))
	for _, move := range rankedMoves {
		row := move.Tile.Row
		col := move.Tile.Col
		if !duplicates[row][col] {
			duplicates[row][col] = true
			filtered = append(filtered, move)
		}
	}
	return filtered
}

func (a *Agent) EvaluateLoop(board Board, maxDepth int) float64 {
	if len(a.stack) > 0 {
		a.stack = a.stack[:0]
	}

	var heuristic float64
	for depthLimit := 1; depthLimit < maxDepth; depthLimit++ {
		heuristic = a.evaluateLoop(board, depthLimit)
	}
	return heuristic
}

func (a *Agent) evaluateLoop(initialBoard Board, startDepth int) float64 {
	a.stack = append(a.stack, StackFrame{Board: initialBoard, Depth: startDepth, Alpha: -Inf, Beta: Inf})

	var heuristic float64 = 0

	for len(a.stack) > 0 {
		frame := &a.stack[len(a.stack)-1]
		currBoard := frame.Board

		if !frame.HasChildren() {
			// Terminal node or timeout
			if frame.Depth == 0 || (frame.Depth >= MinDepth && time.Now().After(a.stopTime)) {
				heuristic = FindHeuristic(currBoard)
				a.stack = a.stack[:len(a.stack)-1] // pop
				continue
			}

			moves := currBoard.FindCurrentMoves()

			if len(moves) == 0 {
				currBoard.IsBlackMove = !currBoard.IsBlackMove
				moves = currBoard.FindCurrentMoves()
				if len(moves) == 0 {
					heuristic = FindHeuristic(currBoard)
					a.stack = a.stack[:len(a.stack)-1]
					continue
				}
			}

			hashKey := a.table.Hash(currBoard)

			if node, ok := a.table.Get(hashKey); ok && node.Depth >= frame.Depth {
				heuristic = node.Heuristic
				a.stack = a.stack[:len(a.stack)-1]
				continue
			}

			children := make([]Board, 0, len(moves))
			for _, move := range moves {
				child := currBoard.MakeMoved(move)
				a.nodesVisited++
				children = append(children, child)
			}

			if len(children) > 0 {
				frame.Children = children
				frame.HashKey = hashKey
				sf := StackFrame{
					Board: children[0],
					Depth: frame.Depth - 1,
					Alpha: frame.Alpha,
					Beta:  frame.Beta,
				}
				a.stack = append(a.stack, sf)
				frame.Index = 1
			} else {
				if currBoard.IsBlackMove {
					a.table.Put(Node{Key: hashKey, Heuristic: frame.Alpha, Depth: frame.Depth})
					heuristic = frame.Alpha
				} else {
					a.table.Put(Node{Key: hashKey, Heuristic: frame.Beta, Depth: frame.Depth})
					heuristic = frame.Beta
				}
				a.stack = a.stack[:len(a.stack)-1]
			}
		} else {
			doPrune := false

			if currBoard.IsBlackMove {
				frame.Alpha = math.Max(frame.Alpha, heuristic)
				if frame.Alpha >= frame.Beta {
					doPrune = true
				}
				if frame.HasNext() && !doPrune {
					sf := StackFrame{
						Board: frame.NextBoard(),
						Depth: frame.Depth - 1,
						Alpha: frame.Alpha,
						Beta:  frame.Beta,
					}
					a.stack = append(a.stack, sf)
				} else {
					a.table.Put(Node{Key: frame.HashKey, Heuristic: frame.Alpha, Depth: frame.Depth})
					heuristic = frame.Alpha
					a.stack = a.stack[:len(a.stack)-1]
				}
			} else {
				frame.Beta = math.Min(frame.Beta, heuristic)
				if frame.Beta <= frame.Alpha {
					doPrune = true
				}
				if frame.HasNext() && !doPrune {
					sf := StackFrame{
						Board: frame.NextBoard(),
						Depth: frame.Depth - 1,
						Alpha: frame.Alpha,
						Beta:  frame.Beta,
					}
					a.stack = append(a.stack, sf)
				} else {
					a.table.Put(Node{Key: frame.HashKey, Heuristic: frame.Beta, Depth: frame.Depth})
					heuristic = frame.Beta
					a.stack = a.stack[:len(a.stack)-1]
				}
			}
		}
	}

	return heuristic
}

func findHeuristic(blackScore, whiteScore float64) float64 {
	return (blackScore - whiteScore) / (blackScore + whiteScore)
}

func FindHeuristic(board Board) float64 {
	return 50*findParityHeuristic(board) + 100*findCornerHeuristic(board) + 100*findMobilityHeuristic(board) + 50*findXcHeuristic(board)
}

func findParityHeuristic(board Board) float64 {
	var whiteScore, blackScore float64
	for row := 0; row < BoardSize; row++ {
		for col := 0; col < BoardSize; col++ {
			switch board.GetSquare(row, col) {
			case White:
				whiteScore++
			case Black:
				blackScore++
			}
		}
	}
	return findHeuristic(blackScore, whiteScore)
}

func findTilesHeuristic(board Board, tiles [][2]int) float64 {
	var whiteTiles, blackTiles float64
	for _, tile := range tiles {
		color := board.GetSquare(tile[0], tile[1])
		switch color {
		case White:
			whiteTiles++
		case Black:
			blackTiles++
		}
	}
	if whiteTiles+blackTiles == 0 {
		return 0
	}
	return findHeuristic(blackTiles, whiteTiles)
}

func findCornerHeuristic(board Board) float64 {
	return findTilesHeuristic(board, Corners)
}

func findXcHeuristic(board Board) float64 {
	return findTilesHeuristic(board, XcSquares)
}

func findMobilityHeuristic(board Board) float64 {
	whiteMoves := float64(board.CountPotentialMoves(White))
	blackMoves := float64(board.CountPotentialMoves(Black))
	if whiteMoves+blackMoves == 0 {
		return 0
	}
	return findHeuristic(blackMoves, whiteMoves)
}
