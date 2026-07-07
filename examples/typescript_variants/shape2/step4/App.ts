class App{
    check = (shape : Shape) => {
        switch (shape.constructor) {
            case Circle:
                return "This should be a Circle"
            default:
                return "Unknown Shape"
        }
    }

    printShape(shape : Shape){
        console.log(shape.getName + " has the area: " + shape.getArea)
    }
}

const app = new App()
const circ = new Circle(5)

console.log(app.check(circ))
app.printShape(circ)