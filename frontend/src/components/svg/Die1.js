import React from "react";
import {playerColorMap} from "../../utility/constants";

function Die1(props) {
    return (
        <svg xmlns="http://www.w3.org/2000/svg" version="1.1" viewBox="0 0 36 36">
            <g
                fillRule="evenodd"
                stroke="#000"
                strokeWidth="1"
                transform="translate(-4.5 -966.86)"
            >
                <path
                    fill={playerColorMap[props.playerId]}
                    fillOpacity=".25"
                    d="M5 5H40V40H5z"
                    transform="translate(0 962.36)"
                ></path>
                <path
                    fill="#000"
                    d="M25 984.86a2.5 2.5 0 11-5 0 2.5 2.5 0 015 0z"
                ></path>
            </g>
        </svg>
    );
}

export default Die1;
