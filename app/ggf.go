package app

import (
	"errors"
	"fmt"
	"golang.org/x/exp/slices"
	"strings"
)

const eof = 0

const expGM = "Othello"

var expTTY = fmt.Sprintf("%d", BoardSize)
var expBo = fmt.Sprintf("%d %s *", BoardSize, InitialBoard.Line())

var ErrEofGGF = errors.New("reached eof while parsing GGF")

func makeMLErr(m string) error {
	return fmt.Errorf("invalid move sequence: %s", m)
}

type ggfScanner struct {
	str   string
	index int
}

func (s *ggfScanner) read() uint8 {
	if s.index >= len(s.str) {
		return eof
	}
	ch := s.str[s.index]
	s.index += 1
	return ch
}

func (s *ggfScanner) peek() uint8 {
	if s.index >= len(s.str) {
		return eof
	}
	return s.str[s.index]
}

func (s *ggfScanner) assert(exp string) error {
	for _, expCh := range exp {
		ch := s.read()
		if ch != uint8(expCh) {
			return fmt.Errorf("%d: expected %c, but got %c", s.index, expCh, ch)
		}
	}
	return nil
}

func (s *ggfScanner) readUntil(ch uint8) (string, error) {
	start := s.index
	for {
		switch s.peek() {
		case eof:
			return "", ErrEofGGF
		case ch:
			fragment := s.str[start:s.index]
			s.read()
			return fragment, nil
		default:
			s.read()
		}
	}
}

func UnmarshalGGF(str string) (OthelloGame, error) {
	o := OthelloGame{Board: MakeInitialBoard()}
	s := ggfScanner{str: str}
	var prevMoveKind MoveKind

	writeMove := func(value string, isBlackMove bool) error {
		tile, err := ParseTileSafe(value)
		if err != nil || !slices.Contains(o.Board.FindCurrentMoves(), tile) {
			return makeMLErr(fmt.Sprintf("illegal move: %v", tile))
		}

		if prevMoveKind != Regular {
			return makeMLErr(fmt.Sprintf("illegal move: %v, expected pass", tile))
		}
		if o.Board.IsBlackMove != isBlackMove {
			return makeMLErr(fmt.Sprintf("illegal move: %v, turn is invalid", tile))
		}

		prevMoveKind = o.MakeMove(tile)
		return nil
	}

	writePair := func(key string, value string) error {
		var err error
		switch key {
		case "GM":
			if value != expGM {
				err = fmt.Errorf("expected field GM to be %s, was %s", expGM, value)
			}
		case "PB":
			o.BlackPlayer = Player{Name: value}
		case "PW":
			o.WhitePlayer = Player{Name: value}
		case "TY":
			if value != expTTY {
				err = fmt.Errorf("expected field TY to be %s, was %s", expTTY, value)
			}
		case "BO":
			if value != expBo {
				err = fmt.Errorf("expected field BO to be %s, was %s", expBo, value)
			}
		case "PA":
			if prevMoveKind != Pass {
				err = makeMLErr("illegal move")
			}
		case "B":
			err = writeMove(value, true)
		case "W":
			err = writeMove(value, false)
		}
		return err
	}

	if err := s.assert("(;"); err != nil {
		return o, err
	}
	for {
		switch s.peek() {
		case eof:
			return o, ErrEofGGF
		case ';':
			return o, s.assert(";)")
		default:
			key, err := s.readUntil('[')
			if err != nil {
				return o, fmt.Errorf("invalid key: %w", err)
			}
			value, err := s.readUntil(']')
			if err != nil {
				return o, fmt.Errorf("invalid value: %w", err)
			}
			if err := writePair(key, value); err != nil {
				return o, err
			}
		}
	}
}

func (b *OthelloBoard) Line() string {
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
