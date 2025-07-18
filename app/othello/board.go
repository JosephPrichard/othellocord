package othello

import (
	"strconv"
	"strings"
)

const BoardSize = 8
const HalfSize = BoardSize / 2
const Empty = 0
const White = 1
const Black = 2

var directions = [][]int{{0, 1}, {0, -1}, {1, 0}, {-1, 0}, {-1, -1}, {-1, 1}, {1, -1}, {1, 1}}
var tiles = makeTiles()

type Tile struct {
	Row int
	Col int
}

type Move struct {
	Tile
	H float64
}

type Board struct {
	IsBlackMove bool
	boardA      uint64
	boardB      uint64
}

func InitialBoard() Board {
	var b Board
	b.SetSquare(BoardSize/2-1, BoardSize/2-1, White)
	b.SetSquare(BoardSize/2, BoardSize/2, White)
	b.SetSquare(BoardSize/2-1, BoardSize/2, Black)
	b.SetSquare(BoardSize/2, BoardSize/2-1, Black)
	return b
}

func InBounds(row int, col int) bool {
	return row >= 0 && col >= 0 && row < BoardSize && col < BoardSize
}

func makeTiles() []Tile {
	var tiles []Tile
	for row := 0; row < BoardSize; row++ {
		for col := 0; col < BoardSize; col++ {
			tiles = append(tiles, Tile{Row: row, Col: col})
		}
	}
	return tiles
}

func (b *Board) WhiteScore() int {
	return b.countDiscs(White)
}

func (b *Board) BlackScore() int {
	return b.countDiscs(Black)
}

func (b *Board) countDiscs(color byte) int {
	discs := 0
	for _, tile := range tiles {
		c := b.GetSquareByTile(tile)
		if c == color {
			discs++
		}
	}
	return discs
}

func (b *Board) FindCurrentMoves() []Tile {
	var moves []Tile
	b.OnCurrentMoves(func(tile Tile) {
		moves = append(moves, tile)
	})
	return moves
}

func (b *Board) CountPotentialMoves(color byte) int {
	count := 0
	b.OnPotentialMoves(color, func(tile Tile) {
		count++
	})
	return count
}

func (b *Board) OnCurrentMoves(onMove func(Tile)) {
	var currColor byte
	if b.IsBlackMove {
		currColor = Black
	} else {
		currColor = White
	}
	b.OnPotentialMoves(currColor, onMove)
}

func (b *Board) OnPotentialMoves(color byte, onMove func(Tile)) {
	var oppColor byte
	if b.IsBlackMove {
		oppColor = White
	} else {
		oppColor = Black
	}

	// check each tile for potential flanks
	for _, tile := range tiles {
		if b.GetSquareByTile(tile) != color {
			// skip any discs of a different color
			continue
		}
		// check each direction from tile for potential flank
		for _, direction := range directions {
			row := tile.Row + direction[0]
			col := tile.Col + direction[1]

			// iterate from tile to next opposite color
			count := 0
			for InBounds(row, col) {
				if b.GetSquare(row, col) != oppColor {
					break
				}
				row += direction[0]
				col += direction[1]
				count++
			}
			// add move to potential moves list assuming
			// we flank at least once tile, the tile is in bounds and is empty
			if count > 0 && InBounds(row, col) && b.GetSquare(row, col) == Empty {
				onMove(Tile{Row: row, Col: col})
			}
		}
	}
}

func (b *Board) MakeMoved(move Tile) Board {
	b2 := *b
	b2.MakeMove(move)
	return b2
}

func (b *Board) MakeMove(move Tile) {
	var oppColor byte
	var currColor byte
	if b.IsBlackMove {
		oppColor = White
		currColor = Black
	} else {
		oppColor = Black
		currColor = White
	}

	b.SetSquareByTile(move, currColor)

	for _, direction := range directions {
		initialRow := move.Row + direction[0]
		initialCol := move.Col + direction[1]

		row := initialRow
		col := initialCol

		flank := false

		// iterate from tile until first potential flank
		for InBounds(row, col) {
			tile := b.GetSquare(row, col)
			if tile == currColor {
				flank = true
				break
			} else if tile == Empty {
				break
			}
			row += direction[0]
			col += direction[1]
		}

		if !flank {
			continue
		}

		row = initialRow
		col = initialCol

		// iterate from tile until first potential flank
		for InBounds(row, col) {
			if b.GetSquare(row, col) != oppColor {
				break
			}
			b.SetSquare(row, col, currColor)

			row += direction[0]
			col += direction[1]
		}
	}

	b.IsBlackMove = !b.IsBlackMove
}

func (b *Board) SetSquare(row, col int, color byte) {
	x := row
	if row >= HalfSize {
		x = row - HalfSize
	}
	p := (x*BoardSize + col) * 2

	clearMask := ^(uint64(1) << p) & ^(uint64(1) << (p + 1))
	if row < HalfSize {
		b.boardA &= clearMask
		b.boardA |= uint64(color) << p
	} else {
		b.boardB &= clearMask
		b.boardB |= uint64(color) << p
	}
}

func (b *Board) GetSquare(row, col int) byte {
	x := row
	if row >= HalfSize {
		x = row - HalfSize
	}
	p := uint64((x*BoardSize + col) * 2)

	mask := uint64((1 << 2) - 1)
	if row < HalfSize {
		return byte((b.boardA >> p) & mask)
	}
	return byte((b.boardB >> p) & mask)
}

func (b *Board) SetSquareByPosition(position int, color byte) {
	b.SetSquare(position/BoardSize, position%BoardSize, color)
}

func (b *Board) GetSquareByPosition(position int) byte {
	return b.GetSquare(position/BoardSize, position%BoardSize)
}

func TileFromNotation(s string) Tile {
	// Example "a1" → Col: 0, Row: 0 (assuming standard board)
	col := int(s[0] - 'a')
	row := int(s[1] - '1')
	return Tile{Row: row, Col: col}
}

func (b *Board) SetSquareByTile(tile Tile, color byte) {
	b.SetSquare(tile.Row, tile.Col, color)
}

func (b *Board) GetSquareByTile(tile Tile) byte {
	return b.GetSquare(tile.Row, tile.Col)
}

func (b *Board) SetSquareByString(square string, color byte) {
	b.SetSquareByTile(TileFromNotation(square), color)
}

func (b *Board) GetSquareByString(square string) byte {
	return b.GetSquareByTile(TileFromNotation(square))
}

func (b *Board) String() string {
	var sb strings.Builder
	sb.WriteString("  ")
	for i := 0; i < BoardSize; i++ {
		sb.WriteRune('a' + rune(i))
		sb.WriteString(" ")
	}
	sb.WriteRune('\n')
	for row := 0; row < BoardSize; row++ {
		sb.WriteString(strconv.Itoa(row + 1))
		sb.WriteString(" ")
		for col := 0; col < BoardSize; col++ {
			sb.WriteString(strconv.Itoa(int(b.GetSquare(row, col))))
			sb.WriteString(" ")
		}
		sb.WriteString("\n")
	}
	return sb.String()
}
