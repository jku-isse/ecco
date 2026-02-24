abstract class Shape{
    abstract getName() : string
    abstract getArea() : number
}

class Circle extends Shape{
    radius : number = 2
    
    getName(): string {
        return "Rectangle {"+this.radius+"}"
    }
    getArea(): number {
        return this.radius * 2 * Math.PI
    }
}

let areas : Array<Shape> = []
areas.push(new Circle())

areas.forEach(s => console.log(s.getName + " has an area of " + s.getArea))