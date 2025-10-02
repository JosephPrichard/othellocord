package app

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

var TestMoves = []ColorMove{
	{Notation: "f1", Color: White},
	{Notation: "d2", Color: White},
	{Notation: "e2", Color: White},
	{Notation: "c3", Color: Black},
	{Notation: "d3", Color: White},
	{Notation: "e3", Color: Black},
	{Notation: "a4", Color: Black},
	{Notation: "b4", Color: Black},
	{Notation: "c4", Color: Black},
	{Notation: "d4", Color: White},
	{Notation: "e4", Color: Black},
	{Notation: "d5", Color: White},
	{Notation: "e5", Color: Black},
	{Notation: "f5", Color: Black},
	{Notation: "e6", Color: Black},
	{Notation: "e7", Color: Black},
}

func TestBoard_FindCurrentMoves(t *testing.T) {
	type Test struct {
		moves    []ColorMove
		expMoves []string
	}

	initialBoard := MakeInitialBoard()

	tests := []Test{
		{
			moves:    []ColorMove{},
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
		preMoves  []ColorMove
		move      Tile
		postMoves []ColorMove
	}
	tests := []Test{
		{
			preMoves: TestMoves,
			move:     ParseTile("c5"),
			postMoves: []ColorMove{
				{Notation: "c5", Color: Black},
				{Notation: "d4", Color: Black},
				{Notation: "d5", Color: Black},
			},
		},
	}

	initialBoard := MakeInitialBoard()

	for i, test := range tests {
		t.Run(fmt.Sprintf("%d", i), func(t *testing.T) {
			board := initialBoard
			for _, move := range test.preMoves {
				board = board.SetSquareByNotation(move)
			}
			t.Logf("board:\n %v", board.String())

			boardAfter := board.MakeMoved(test.move)
			t.Logf("boardAfter:\n %v", boardAfter.String())

			var expBoard OthelloBoard
			for _, move := range test.postMoves {
				expBoard = boardAfter.SetSquareByNotation(move)
			}
			t.Logf("expBoard:\n %v", expBoard.String())

			assert.Equal(t, expBoard, boardAfter)
		})
	}
}

func TestMoveList_UnmarshalStrings(t *testing.T) {
	type Test struct {
		MoveListStr string
		MoveList    []Move
	}

	tests := []Test{
		{
			MoveListStr: "a1,a2,a3,a4",
			MoveList:    []Move{{Tile: Tile{Row: 0, Col: 0}}, {Tile: Tile{Row: 1, Col: 0}}, {Tile: Tile{Row: 2, Col: 0}}, {Tile: Tile{Row: 3, Col: 0}}},
		},
		{
			MoveListStr: "a1,",
			MoveList:    []Move{{}},
		},
		{
			MoveListStr: "",
			MoveList:    nil,
		},
	}

	for i, test := range tests {
		t.Run(fmt.Sprintf("%d", i), func(t *testing.T) {
			moveList, err := UnmarshalMoveList(test.MoveListStr)
			if err != nil {
				t.Fatalf("failed to unmarshal game: %v", err)
			}
			assert.Equal(t, test.MoveList, moveList)
		})
	}
}

func TestBoard_MarshalString(t *testing.T) {
	type Test struct {
		Moves  []Tile
		String string
	}

	tests := []Test{
		{
			Moves:  []Tile{},
			String: "b+27wb6bw27",
		},
		{
			Moves:  []Tile{{}, {Row: 1}, {Col: 1}, {Row: 1, Col: 1}},
			String: "b+bb6ww17wb6bw27",
		},
	}

	for i, test := range tests {
		t.Run(fmt.Sprintf("Marshal/%d", i), func(t *testing.T) {
			board := MakeInitialBoard()
			for _, move := range test.Moves {
				board.MakeMove(move)
			}

			str := board.MarshalString()
			t.Logf("\n%s\n", board.String())

			assert.Equal(t, test.String, str)
		})
	}
	for i, test := range tests {
		t.Run(fmt.Sprintf("Unmarshal/%d", i), func(t *testing.T) {
			expBoard := MakeInitialBoard()
			for _, move := range test.Moves {
				expBoard.MakeMove(move)
			}

			board, err := UnmarshalBoard(test.String)
			if err != nil {
				t.Fatalf("failed to unmarshal string: %v", err)
			}
			t.Logf("\n%s\n", board.String())

			assert.Equal(t, expBoard, board)
		})
	}
}
