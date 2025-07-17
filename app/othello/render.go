package othello

import (
	"fmt"
	"github.com/golang/freetype/truetype"
	"github.com/llgcode/draw2d"
	"github.com/llgcode/draw2d/draw2dimg"
	"github.com/llgcode/draw2d/draw2dkit"
	"image"
	"image/color"
	"image/draw"
	"math"
	"strconv"
)

const (
	DiscSize      = 100
	LineThickness = 4
	SideOffset    = 40
	TileSize      = DiscSize * LineThickness
	DotSize       = 16
	TopLeft       = 5
)

var (
	GreenColor   = color.RGBA{R: 82, G: 127, B: 85, A: 255}
	BlackColor   = color.RGBA{R: 0, G: 0, B: 0, A: 255}
	CyanColor    = color.RGBA{R: 255, G: 255, B: 0, A: 255}
	YellowColor  = color.RGBA{R: 0, G: 0, B: 0, A: 255}
	OutlineColor = color.RGBA{R: 40, G: 40, B: 40, A: 255}
	BlackFill    = color.RGBA{R: 20, G: 20, B: 20, A: 255}
	WhiteFill    = color.RGBA{R: 250, G: 250, B: 250, A: 255}
	NoFill       = color.RGBA{R: 255, G: 255, B: 255, A: 255}
	FontSize     = 28.0
	DotLocations = [][]int{{2, 2}, {6, 6}, {2, 6}, {6, 2}}
)

type Fonts struct {
	Font        *truetype.Font
	FontExtents draw2dimg.FontExtents
}

func NewFonts() Fonts {
	fontData := draw2d.FontData{Name: "Courier", Family: draw2d.FontFamilyMono, Style: draw2d.FontStyleBold}
	font := draw2d.GetFont(fontData)
	if font == nil {
		panic(fmt.Sprintf("could not get font: %v", fontData))
	}
	return Fonts{
		Font:        font,
		FontExtents: draw2dimg.Extents(font, FontSize),
	}
}

type Renderer struct {
	whiteDisc  image.Image
	blackDisc  image.Image
	noDisc     image.Image
	background image.Image
	fonts      Fonts
}

func NewRenderer() Renderer {
	fonts := NewFonts()
	return Renderer{
		whiteDisc:  drawDisc(WhiteFill, 1.0),
		blackDisc:  drawDisc(BlackFill, 1.0),
		noDisc:     drawDisc(NoFill, 2.0),
		background: drawBackground(fonts, BoardSize),
		fonts:      fonts,
	}
}

func (r Renderer) DrawBoard(board Board) image.Image {
	return r.DrawBoardMoves(board, nil)
}

func (r Renderer) DrawBoardMoves(board Board, moves []Tile) image.Image {
	img := image.NewRGBA(image.Rect(0, 0, r.background.Bounds().Dx(), r.background.Bounds().Dy()))

	r.DrawBoardDiscs(board, img)

	// draw each move image onto the board
	for _, move := range moves {
		x := SideOffset + LineThickness + move.Col*TileSize
		y := SideOffset + LineThickness + move.Row*TileSize
		draw.Draw(img, r.noDisc.Bounds(), r.noDisc, image.Point{X: x, Y: y}, draw.Over)
	}

	return img
}

func (r Renderer) DrawBoardAnalysis(board Board, bestMoves []Move) image.Image {
	img := image.NewRGBA(image.Rect(0, 0, r.background.Bounds().Dx(), r.background.Bounds().Dy()))

	r.DrawBoardDiscs(board, img)

	g := draw2dimg.NewGraphicContext(img)

	// draw each heuristic eval onto the board
	for i, move := range bestMoves {
		hText := fmt.Sprintf("%f", move.H)
		minLen := 5.0
		if move.H >= 0.0 {
			minLen = 4.0
		}
		end := int(math.Min(float64(len(hText)), minLen))
		hText = hText[0:end]

		if i == 0 {
			g.SetFillColor(CyanColor)
		} else {
			g.SetFillColor(YellowColor)
		}

		x := SideOffset + move.Col*TileSize
		y := SideOffset + move.Row*TileSize
		drawCenterString(g, r.fonts, hText, x, y, TileSize, TileSize)
	}

	return img
}

func (r Renderer) DrawBoardDiscs(board Board, img draw.Image) {
	draw.Draw(img, r.background.Bounds(), r.background, image.Point{X: 0, Y: 0}, draw.Over)

	// draw discs onto board, either empty, black, or white
	for _, tile := range tiles {
		point := image.Point{
			X: SideOffset + LineThickness + tile.Col*TileSize,
			Y: SideOffset + LineThickness + tile.Row*TileSize,
		}
		// determine which bitmap belongs in the tile slot
		disc := board.GetSquareByTile(tile)
		if disc == Black {
			draw.Draw(img, r.blackDisc.Bounds(), r.blackDisc, point, draw.Over)
		} else if disc == White {
			draw.Draw(img, r.whiteDisc.Bounds(), r.whiteDisc, point, draw.Over)
		}
	}
}

func drawBackground(fonts Fonts, boardSize int) image.Image {
	width := TileSize*boardSize + LineThickness + SideOffset
	height := TileSize*boardSize + LineThickness + SideOffset

	img := image.NewRGBA(image.Rect(0, 0, width, height))

	for x := SideOffset; x < width; x++ {
		for y := SideOffset; y < height; y++ {
			img.Set(x, y, GreenColor)
		}
	}

	// draw black horizontal lines
	for i := 0; i < boardSize+1; i++ {
		for x := SideOffset; x < width; x++ {
			y := i*TileSize + SideOffset
			for j := 0; j < LineThickness; j++ {
				img.Set(x, y+j, BlackColor)
			}
		}
	}

	// draw black vertical lines
	for i := 0; i < boardSize+1; i++ {
		for y := SideOffset; y < height; y++ {
			x := i*TileSize + SideOffset
			for j := 0; j < LineThickness; j++ {
				img.Set(x+j, y, BlackColor)
			}
		}
	}

	g := draw2dimg.NewGraphicContext(img)
	g.SetFillColor(WhiteFill)

	// draw letters on horizontal sidebar
	for i := 0; i < boardSize; i++ {
		text := string(rune(i) + 'A')
		x := SideOffset + i*TileSize
		drawCenterString(g, fonts, text, x, 0, TileSize, SideOffset)
	}

	// draw numbers on vertical sidebar
	for i := 0; i < boardSize; i++ {
		text := strconv.Itoa(i + 1)
		y := SideOffset + i*TileSize
		drawCenterString(g, fonts, text, 0, y, SideOffset, TileSize)
	}

	g.SetFillColor(BlackFill)
	for _, location := range DotLocations {
		// fills an oval, the x,y is subtracted by the dot size and line thickness to center it
		col := location[0]
		row := location[1]
		x := SideOffset + col*TileSize - DotSize/2 + LineThickness/2
		y := SideOffset + row*TileSize - DotSize/2 + LineThickness/2
		draw2dkit.Circle(g, float64(x), float64(y), DotSize)
	}

	return img
}

func drawCenterString(g *draw2dimg.GraphicContext, fonts Fonts, text string, x, y, width, height int) {
	g.SetFont(fonts.Font)
	g.SetFontSize(FontSize)

	left, _, right, _ := g.GetStringBounds(text)
	strWidth := right - left
	// Determine the X coordinate for the text
	xDraw := float64(x) + (float64(width)-strWidth)/2
	// Determine the Y coordinate for the text (note we add the ascent, as in java 2d 0 is top of the screen)
	yDraw := float64(y) + ((float64(height) - fonts.FontExtents.Height) / 2) + fonts.FontExtents.Ascent

	g.FillStringAt(text, xDraw, yDraw)
}

func drawDisc(fillColor color.RGBA, thickness float64) image.Image {
	img := image.NewRGBA(image.Rect(0, 0, DiscSize, DiscSize))

	g := draw2dimg.NewGraphicContext(img)

	g.SetFillColor(fillColor)
	g.SetStrokeColor(OutlineColor)
	g.SetLineWidth(thickness)
	draw2dkit.Circle(g, float64(TopLeft), float64(TopLeft), DiscSize-TopLeft*2)

	return img
}
