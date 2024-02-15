import {readFileSync} from 'node:fs';
const input = readFileSync('input.txt').toString().split('\n');

const part1 = (lines:[string]) : number => {
    const nums = lines.map(x=>x.replace(/\D/g, ""))
                      .map(x=>x.charAt(0)+x.charAt(x.length-1))
                      .map(x=>parseInt(x))
                      .filter(x=>isNaN(x)===false)
                      .reduce((a,b)=>a+b);
    return nums
};

const p1 = part1(input);
console.log("Part 1: " + p1);