const jwt = require('jsonwebtoken');
const User = require('../models/User');

module.exports = async (req, res, next) => {
    try {
        // 토큰 가져오기
        const token = req.header('Authorization')?.replace('Bearer ', '');
        
        if (!token) {
            return res.status(401).json({ message: '인증이 필요합니다.' });
        }

        // 토큰 검증
        const decoded = jwt.verify(token, process.env.JWT_SECRET);
        
        // 사용자 찾기
        const user = await User.findById(decoded.userId);
        
        if (!user) {
            return res.status(401).json({ message: '유효하지 않은 사용자입니다.' });
        }

        // 요청 객체에 사용자 정보 추가
        req.user = user;
        next();
    } catch (error) {
        res.status(401).json({ message: '인증에 실패했습니다.' });
    }
}; 