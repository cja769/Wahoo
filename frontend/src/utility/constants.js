export default function getBackendUrl() {
    if (process.env.NODE_ENV === 'development') {
        return "http://wahoo.jay.com:8082"
    }
    return "http://wahoo.the-boys-pickem.com"
};

export const playerColorMap = {
    'p0' : 'red',
    'p1' : 'blue',
    'p2' : 'green',
    'p3' : 'yellow'
};