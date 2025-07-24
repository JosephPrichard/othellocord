package othello

import "testing"

func BenchmarkEngine_FindBestMove(b *testing.B) {
	for i := range b.N {
		board := InitialBoard()
		engine := NewEngine()
		b.Logf("starting benchmark %d", i+1)
		for j := 0; j < 10; j++ {
			move, ok := engine.FindBestMove(board, 10)
			if !ok {
				b.Fatalf("no moves at board: %s", board.String())
			}
			board.MakeMove(move.Tile)
		}
	}
}
