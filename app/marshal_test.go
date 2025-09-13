package app

import (
	"fmt"
	"github.com/stretchr/testify/assert"
	"testing"
)

func TestGame_MarshalGGF(t *testing.T) {
	game := OthelloGame{WhitePlayer: Player{ID: "id1", Name: "Player1"}, BlackPlayer: Player{ID: "id2", Name: "Player2"}, Board: InitialBoard()}

	game.MakeMove(Tile{})
	game.MakeMove(Tile{Row: 1})
	game.MakeMove(Tile{Col: 1})
	game.MakeMove(Tile{Row: 1, Col: 1})

	str := game.MarshalGGF()

	assert.Equal(t, str, "(;GM[Othello]PB[Player2]PW[Player1]TY[8]BO[8 ---------------------------O*------*O--------------------------- *]B[A1]W[A2]B[B1]W[B2])")
}

func TestGame_UnmarshalStrings(t *testing.T) {
	type Test struct {
		MoveListStr string
		MoveList    []Tile
	}

	tests := []Test{
		{
			MoveListStr: "a1,a2,a3,a4",
			MoveList:    []Tile{{Row: 0, Col: 0}, {Row: 1, Col: 0}, {Row: 2, Col: 0}, {Row: 3, Col: 0}},
		},
		{
			MoveListStr: "a1,",
			MoveList:    []Tile{{}},
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
			board := InitialBoard()
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
			expBoard := InitialBoard()
			for _, move := range test.Moves {
				expBoard.MakeMove(move)
			}

			var board OthelloBoard
			err := board.UnmarshalString(test.String)
			if err != nil {
				t.Fatalf("failed to unmarshal string: %v", err)
			}
			t.Logf("\n%s\n", board.String())

			assert.Equal(t, expBoard, board)
		})
	}
}
