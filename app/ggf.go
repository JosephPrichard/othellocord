package app

import (
	"fmt"
	"strings"
)

var expBo = fmt.Sprintf("%d %s *", BoardSize, InitialBoard.MarshallGGF())

func (b *OthelloBoard) MarshallGGF() string {
	var sb strings.Builder
	for row := 0; row < BoardSize; row++ {
		for col := 0; col < BoardSize; col++ {
			str := "-"
			switch b.GetSquare(row, col) {
			case White:
				str = "O"
			case Black:
				str = "*"
			}
			sb.WriteString(str)
		}
	}
	return sb.String()
}

func (o *OthelloGame) MarshalGGF() string {
	var sb strings.Builder

	sb.WriteString("(;GM[Othello]")
	sb.WriteString("PB")
	fmt.Fprintf(&sb, "[%s]", o.BlackPlayer.Name)
	sb.WriteString("PW")
	fmt.Fprintf(&sb, "[%s]", o.WhitePlayer.Name)
	fmt.Fprintf(&sb, "TY[%d]", BoardSize)
	fmt.Fprintf(&sb, "BO[%s]", expBo)

	for i, move := range o.MoveList {
		if i%2 == 0 {
			sb.WriteString("B")
		} else {
			sb.WriteString("W")
		}
		sb.WriteString("[")
		sb.WriteString(move.String())
		sb.WriteString("]")
	}
	sb.WriteString(";)")

	return sb.String()
}
