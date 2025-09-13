package app

import (
	"errors"
	"strconv"
	"strings"
)

func (o *OthelloGame) MarshalGGF() string {
	var sb strings.Builder

	sb.WriteString("(;GM[Othello]")
	sb.WriteString("PB[")
	sb.WriteString(o.BlackPlayer.Name)
	sb.WriteString("]PW[")
	sb.WriteString(o.WhitePlayer.Name)
	// this can be hard coded because this bot only handles 8x8 board sizes. this needs to be changed if we add support for different board sizes
	sb.WriteString("]TY[8]BO[8 ---------------------------O*------*O--------------------------- *]")
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
	sb.WriteString(")")

	return sb.String()
}

func UnmarshalMoveList(moveListStr string) ([]Tile, error) {
	var moveList []Tile

	isSplit := func(r rune) bool {
		return r == ','
	}
	for tileMove := range strings.FieldsFuncSeq(moveListStr, isSplit) {
		move, err := ParseTileSafe(tileMove)
		if err != nil {
			return nil, err
		}
		moveList = append(moveList, move)
	}

	return moveList, nil
}

func MarshalMoveList(moveList []Tile) string {
	var sb strings.Builder

	for _, move := range moveList {
		sb.WriteString(move.String())
		sb.WriteRune(',')
	}

	return sb.String()
}

func (b *OthelloBoard) MarshalString() string {
	var sb strings.Builder

	if b.IsBlackMove {
		sb.WriteString("b")
	} else {
		sb.WriteString("w")
	}
	sb.WriteString("+")

	emptyCount := 0

	writeEmpty := func() {
		if emptyCount > 0 {
			sb.WriteString(strconv.Itoa(emptyCount))
		}
		emptyCount = 0
	}

	for row := 0; row < BoardSize; row++ {
		for col := 0; col < BoardSize; col++ {
			t := b.GetSquare(row, col)
			switch t {
			case Empty:
				emptyCount++
			case Black:
				writeEmpty()
				sb.WriteRune('b')
			case White:
				writeEmpty()
				sb.WriteRune('w')
			}
		}
	}
	writeEmpty()

	return sb.String()
}

var ErrBoardUnmarshal = errors.New("failed to unmarshal board from string")

func (b *OthelloBoard) UnmarshalString(str string) error {
	var board OthelloBoard
	tileIndex := 0
	for strIndex := 0; strIndex < len(str); {
		ch := str[strIndex]
		switch strIndex {
		case 0:
			switch ch {
			case 'b':
				board.IsBlackMove = true
			case 'w':
				board.IsBlackMove = false
			default:
				return ErrBoardUnmarshal
			}
			strIndex++
		case 1:
			if ch != '+' {
				return ErrBoardUnmarshal
			}
			strIndex++
		default:
			row := tileIndex / BoardSize
			col := tileIndex % BoardSize
			switch ch {
			case 'b':
				board.SetSquare(row, col, Black)
				tileIndex++
				strIndex++
			case 'w':
				board.SetSquare(row, col, White)
				tileIndex++
				strIndex++
			default:
				firstIndex := strIndex
				for ; strIndex < len(str); strIndex++ {
					ch := str[strIndex]
					if ch == 'w' || ch == 'b' {
						break
					}
				}
				numStr := str[firstIndex:strIndex]
				num, err := strconv.Atoi(numStr)
				if err != nil {
					return ErrBoardUnmarshal
				}
				tileIndex += num
			}
		}
	}
	*b = board
	return nil
}
