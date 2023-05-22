export default function getBackendUrl() {
    console.log(process.env);
    if (process.env.NODE_ENV === 'development') {
        return "http://wahoo.jay.com:8082"
    }
    return "http://wahoo.the-boys-pickem.com:8082"
};