export default function getBackendUrl() {
    if (process.env.NODE_ENV === 'development') {
        return "http://wahoo.jay.com:8082"
    }
    return "http://wahoo.the-boys-pickem.com"
};