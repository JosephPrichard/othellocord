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
	initialBoard := MakeInitialBoard()

	tests := []struct {
		moves    []ColorMove
		expMoves []string
	}{
		{
			moves:    []ColorMove{},
			expMoves: []string{"c4", "d3", "e6", "f5"},
		},
		{
			moves:    TestMoves,
			expMoves: []string{"c1", "c2", "c5", "c6", "e1"},
		},
	}

	for i, tt := range tests {
		t.Run(fmt.Sprintf("%d", i), func(t *testing.T) {
			board := initialBoard
			for _, move := range tt.moves {
				board = board.SetSquareByNotation(move)
			}
			t.Logf("board:\n %v", board.String())

			moves := board.FindCurrentMoves()
			sortTiles(moves)

			var expMoves []Tile
			for _, move := range tt.expMoves {
				expMoves = append(expMoves, ParseTile(move))
			}
			assert.Equal(t, expMoves, moves)
		})
	}
}

func TestBoard_MakeMoved(t *testing.T) {
	tests := []struct {
		name      string
		preMoves  []ColorMove
		move      Tile
		postMoves []ColorMove
	}{
		{
			name:     "MakeMove",
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

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			board := initialBoard
			for _, move := range tt.preMoves {
				board = board.SetSquareByNotation(move)
			}
			t.Logf("board:\n %v", board.String())

			boardAfter := board.MakeMoved(tt.move)
			t.Logf("boardAfter:\n %v", boardAfter.String())

			var expBoard OthelloBoard
			for _, move := range tt.postMoves {
				expBoard = boardAfter.SetSquareByNotation(move)
			}
			t.Logf("expBoard:\n %v", expBoard.String())

			assert.Equal(t, expBoard, boardAfter)
		})
	}
}

func TestMoveList_UnmarshalStrings(t *testing.T) {
	tests := []struct {
		name        string
		moveListStr string
		moveList    []Move
	}{
		{
			name:        "UnmarshalManyMoves",
			moveListStr: "a1,a2,a3,a4",
			moveList:    []Move{{Tile: Tile{Row: 0, Col: 0}}, {Tile: Tile{Row: 1, Col: 0}}, {Tile: Tile{Row: 2, Col: 0}}, {Tile: Tile{Row: 3, Col: 0}}},
		},
		{
			name:        "UnmarshalOneMove",
			moveListStr: "a1,",
			moveList:    []Move{{}},
		},
		{
			name:        "UnmarshalEmpty",
			moveListStr: "",
			moveList:    nil,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			moveList, err := UnmarshalMoveList(tt.moveListStr)
			if err != nil {
				t.Fatalf("failed to unmarshal game: %v", err)
			}
			assert.Equal(t, tt.moveList, moveList)
		})
	}
}

func TestBoard_MarshalString(t *testing.T) {
	tests := []struct {
		name   string
		moves  []Tile
		string string
	}{
		{
			name:   "NoMoves",
			moves:  []Tile{},
			string: "b+27wb6bw27",
		},
		{
			name:   "OneMove",
			moves:  []Tile{{}, {Row: 1}, {Col: 1}, {Row: 1, Col: 1}},
			string: "b+bb6ww17wb6bw27",
		},
	}

	for _, tt := range tests {
		t.Run(fmt.Sprintf("Marshal_%s", tt.name), func(t *testing.T) {
			board := MakeInitialBoard()
			for _, move := range tt.moves {
				board.MakeMove(move)
			}

			str := board.MarshalString()
			t.Logf("\n%s\n", board.String())

			assert.Equal(t, tt.string, str)
		})
	}
	for _, tt := range tests {
		t.Run(fmt.Sprintf("Unmarshal_%s", tt.name), func(t *testing.T) {
			expBoard := MakeInitialBoard()
			for _, move := range tt.moves {
				expBoard.MakeMove(move)
			}

			board, err := UnmarshalBoard(tt.string)
			if err != nil {
				t.Fatalf("failed to unmarshal string: %v", err)
			}
			t.Logf("\n%s\n", board.String())

			assert.Equal(t, expBoard, board)
		})
	}
}
