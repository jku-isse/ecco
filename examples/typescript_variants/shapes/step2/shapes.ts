abstract class Shape{
    abstract getName() : string
    abstract getArea() : number
}

class Rectangle extends Shape{
    width : number = 3
    height : number = 2
    getName(): string {
        return "Rectangle {"+this.width+","+this.height+"}"
    }
    getArea(): number {
        return this.width * this.height
    }
}

let areas : Array<Shape> = []
areas.push(new Rectangle())

areas.forEach(s => console.log(s.getName + " has an area of " + s.getArea))