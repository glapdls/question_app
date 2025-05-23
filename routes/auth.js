const express = require('express');
const router = express.Router();
const passport = require('passport');
const jwt = require('jsonwebtoken');
const User = require('../models/User');

// 회원가입
router.post('/signup', async (req, res) => {
    try {
        const { email, password, name } = req.body;

        // 이메일 중복 체크
        const existingUser = await User.findOne({ email });
        if (existingUser) {
            return res.status(400).json({ message: '이미 등록된 이메일입니다.' });
        }

        // 새 사용자 생성
        const user = new User({
            email,
            password,
            name,
            provider: 'local'
        });

        await user.save();

        // JWT 토큰 생성
        const token = jwt.sign(
            { userId: user._id },
            process.env.JWT_SECRET,
            { expiresIn: '7d' }
        );

        res.status(201).json({
            token,
            user: {
                id: user._id,
                email: user.email,
                name: user.name
            }
        });
    } catch (error) {
        res.status(500).json({ message: '서버 오류가 발생했습니다.' });
    }
});

// 로그인
router.post('/login', async (req, res) => {
    try {
        const { email, password } = req.body;

        // 사용자 찾기
        const user = await User.findOne({ email });
        if (!user) {
            return res.status(401).json({ message: '이메일 또는 비밀번호가 올바르지 않습니다.' });
        }

        // 비밀번호 확인
        const isMatch = await user.comparePassword(password);
        if (!isMatch) {
            return res.status(401).json({ message: '이메일 또는 비밀번호가 올바르지 않습니다.' });
        }

        // 마지막 로그인 시간 업데이트
        user.lastLogin = Date.now();
        await user.save();

        // JWT 토큰 생성
        const token = jwt.sign(
            { userId: user._id },
            process.env.JWT_SECRET,
            { expiresIn: '7d' }
        );

        res.json({
            token,
            user: {
                id: user._id,
                email: user.email,
                name: user.name
            }
        });
    } catch (error) {
        res.status(500).json({ message: '서버 오류가 발생했습니다.' });
    }
});

// 소셜 로그인 라우트
router.get('/google',
    passport.authenticate('google', { scope: ['profile', 'email'] })
);

router.get('/google/callback',
    passport.authenticate('google', { failureRedirect: '/login' }),
    (req, res) => {
        const token = jwt.sign(
            { userId: req.user._id },
            process.env.JWT_SECRET,
            { expiresIn: '7d' }
        );
        res.redirect(`/auth-success?token=${token}`);
    }
);

router.get('/kakao',
    passport.authenticate('kakao')
);

router.get('/kakao/callback',
    passport.authenticate('kakao', { failureRedirect: '/login' }),
    (req, res) => {
        const token = jwt.sign(
            { userId: req.user._id },
            process.env.JWT_SECRET,
            { expiresIn: '7d' }
        );
        res.redirect(`/auth-success?token=${token}`);
    }
);

router.get('/naver',
    passport.authenticate('naver')
);

router.get('/naver/callback',
    passport.authenticate('naver', { failureRedirect: '/login' }),
    (req, res) => {
        const token = jwt.sign(
            { userId: req.user._id },
            process.env.JWT_SECRET,
            { expiresIn: '7d' }
        );
        res.redirect(`/auth-success?token=${token}`);
    }
);

// 로그아웃
router.post('/logout', (req, res) => {
    req.logout();
    res.json({ message: '로그아웃되었습니다.' });
});

module.exports = router; 