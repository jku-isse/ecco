class App{
    check = (shape : Shape) => {
        switch (shape.constructor) {
            case Rectangle:
                return "This should be a Rectangle"
            default:
                return "Unknown Shape"
        }
    }
    
    printShape(shape : Shape){
        console.log(shape.getName + " has the area: " + shape.getArea)
    }
}

const app = new App()
const rect = new Rectangle(3,2)

app.check(rect)
app.printShape(rect)