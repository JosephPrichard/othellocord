package othello

import (
	"fmt"
	"github.com/stretchr/testify/assert"
	"slices"
	"testing"
)

func sortTiles(tiles []Tile) {
	slices.SortFunc(tiles, func(a, b Tile) int {
		aStr := a.String()
		bStr := b.String()
		if aStr < bStr {
			return -1
		} else if aStr > bStr {
			return 1
		}
		return 0
	})
}

var TestMoves = []Move{
	{Notation: "f1", color: White},
	{Notation: "d2", color: White},
	{Notation: "e2", color: White},
	{Notation: "c3", color: Black},
	{Notation: "d3", color: White},
	{Notation: "e3", color: Black},
	{Notation: "a4", color: Black},
	{Notation: "b4", color: Black},
	{Notation: "c4", color: Black},
	{Notation: "d4", color: White},
	{Notation: "e4", color: Black},
	{Notation: "d5", color: White},
	{Notation: "e5", color: Black},
	{Notation: "f5", color: Black},
	{Notation: "e6", color: Black},
	{Notation: "e7", color: Black},
}

func TestBoard_FindCurrentMoves(t *testing.T) {
	type Test struct {
		moves    []Move
		expMoves []string
	}

	initialBoard := InitialBoard()

	tests := []Test{
		{
			moves:    []Move{},
			expMoves: []string{"c4", "d3", "e6", "f5"},
		},
		{
			moves:    TestMoves,
			expMoves: []string{"c1", "c2", "c5", "c6", "e1"},
		},
	}

	for i, test := range tests {
		t.Run(fmt.Sprintf("%d", i), func(t *testing.T) {
			board := initialBoard
			for _, move := range test.moves {
				board = board.SetSquareByNotation(move)
			}
			t.Logf("board:\n %v", board.String())

			moves := board.FindCurrentMoves()
			sortTiles(moves)

			var expMoves []Tile
			for _, move := range test.expMoves {
				expMoves = append(expMoves, ParseTile(move))
			}
			assert.Equal(t, expMoves, moves)
		})
	}
}

func TestBoard_MakeMoved(t *testing.T) {
	type Test struct {
		preMoves  []Move
		move      Tile
		postMoves []Move
	}
	tests := []Test{
		{
			preMoves: TestMoves,
			move:     ParseTile("c5"),
			postMoves: []Move{
				{Notation: "c5", color: Black},
				{Notation: "d4", color: Black},
				{Notation: "d5", color: Black},
			},
		},
	}

	initialBoard := InitialBoard()

	for i, test := range tests {
		t.Run(fmt.Sprintf("%d", i), func(t *testing.T) {
			board := initialBoard
			for _, move := range test.preMoves {
				board = board.SetSquareByNotation(move)
			}
			t.Logf("board:\n %v", board.String())

			boardAfter := board.MakeMoved(test.move)
			t.Logf("boardAfter:\n %v", boardAfter.String())

			var expBoard Board
			for _, move := range test.postMoves {
				expBoard = boardAfter.SetSquareByNotation(move)
			}
			t.Logf("expBoard:\n %v", expBoard.String())

			assert.Equal(t, expBoard, boardAfter)
		})
	}
}
