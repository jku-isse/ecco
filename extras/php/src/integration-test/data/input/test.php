<?php

function test() {

if ($i>6) {
	echo ">6";
} elseif ($i>5) {
	$i=">5";
} elseif ($i>4) {
	$i=">4";
	echo "and also print";
} else {
	echo "nothing";
}
  
}
