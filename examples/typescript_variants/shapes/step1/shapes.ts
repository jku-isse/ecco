abstract class Shape{
    abstract getName() : string
    abstract getArea() : number
}

let areas : Array<Shape> = []

areas.forEach(s => console.log(s.getName + " has an area of " + s.getArea))