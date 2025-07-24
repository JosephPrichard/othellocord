package othello

import (
	"github.com/stretchr/testify/assert"
	"testing"
)

func TestTTable(t *testing.T) {
	getRand := func() int {
		return 2
	}
	tt := NewTTable(12, getRand)

	board1 := InitialBoard()
	board2 := InitialBoard()
	board2.SetSquare(5, 6, Black)
	board3 := InitialBoard()
	board3.SetSquare(1, 6, Black)

	tt.Put(tt.Hash(board1), board1, 25, 5)
	tt.Put(tt.Hash(board2), board2, 20, 10)
	tt.Put(tt.Hash(board3), board3, 15, 3)

	expectedNodes := [2]Node{
		{Set: true, Board: board2, Key: 0, Heuristic: 20, Depth: 10},
		{Set: true, Board: board3, Key: 0, Heuristic: 15, Depth: 3},
	}
	assert.Equal(t, expectedNodes, tt.cache[0])

	node, ok := tt.Get(tt.Hash(board2), board2)
	assert.True(t, ok)
	assert.Equal(t, expectedNodes[0], node)
}
