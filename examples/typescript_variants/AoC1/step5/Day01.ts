import {readFileSync} from 'node:fs';
const input = readFileSync('input.txt').toString().split('\n');

const part2 = (lines:[string]) : number => {
    return lines.map(x=>x.replace(/one/g, "o1e")
                         .replace(/two/g, "t2o")
                         .replace(/three/g, "t3e")    
                         .replace(/four/g, "f4r") 
                         .replace(/five/g, "f5e")
                         .replace(/six/g, "s6x")
                         .replace(/seven/g, "s7n")
                         .replace(/eight/g, "e8t")
                         .replace(/nine/g, "n9e")
                         .replace(/zero/g, "z0o")
                         .replace(/\D/g, ""))
                .map(x=>x.charAt(0)+x.charAt(x.length-1))
                .map(x=>parseInt(x))
                .filter(x=>isNaN(x)===false)
                .reduce((a,b)=>a+b);
}

const p2 = part2(input);
console.log("Part 2:" + p2);