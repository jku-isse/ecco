class App{
    check = (shape : Shape) => {
        switch (shape.constructor) {
            default:
                return "Unknown Shape"
        }
    }

    printShape(shape : Shape){
        console.log(shape.getName + " has the area: " + shape.getArea)
    }
}

const app = new App()