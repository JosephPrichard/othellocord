package app

import (
	"github.com/stretchr/testify/assert"
	"testing"
)

func TestGame_MarshalGGF(t *testing.T) {
	game := OthelloGame{WhitePlayer: Player{ID: "id1", Name: "Player1"}, BlackPlayer: Player{ID: "id2", Name: "Player2"}, Board: MakeInitialBoard()}

	game.MakeMove(Tile{})
	game.MakeMove(Tile{Row: 1})
	game.MakeMove(Tile{Col: 1})
	game.MakeMove(Tile{Row: 1, Col: 1})

	str := game.MarshalGGF()

	assert.Equal(t, str, "(;GM[Othello]PB[Player2]PW[Player1]TY[8]BO[8 ---------------------------O*------*O--------------------------- *]B[A1]W[A2]B[B1]W[B2];)")
}
