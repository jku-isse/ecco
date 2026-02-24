abstract class Shape{
    abstract getName() : string
    abstract getArea() : number
}

class Square extends Shape{
    side : number = 2
    
    getName(): string {
        return "Square {"+this.side+"}"
    }
    getArea(): number {
        return this.side * this.side
    }
}

let areas : Array<Shape> = []
areas.push(new Square())

areas.forEach(s => console.log(s.getName + " has an area of " + s.getArea))