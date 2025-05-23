const passport = require('passport');
const GoogleStrategy = require('passport-google-oauth20').Strategy;
const KakaoStrategy = require('passport-kakao').Strategy;
const NaverStrategy = require('passport-naver').Strategy;
const User = require('../models/User');

// 세션 직렬화
passport.serializeUser((user, done) => {
    done(null, user.id);
});

passport.deserializeUser(async (id, done) => {
    try {
        const user = await User.findById(id);
        done(null, user);
    } catch (error) {
        done(error, null);
    }
});

// Google 전략
passport.use(new GoogleStrategy({
    clientID: process.env.GOOGLE_CLIENT_ID,
    clientSecret: process.env.GOOGLE_CLIENT_SECRET,
    callbackURL: process.env.GOOGLE_CALLBACK_URL
}, async (accessToken, refreshToken, profile, done) => {
    try {
        // 기존 사용자 확인
        let user = await User.findOne({ socialId: profile.id, provider: 'google' });

        if (!user) {
            // 새 사용자 생성
            user = new User({
                email: profile.emails[0].value,
                name: profile.displayName,
                socialId: profile.id,
                provider: 'google',
                profileImage: profile.photos[0].value
            });
            await user.save();
        }

        return done(null, user);
    } catch (error) {
        return done(error, null);
    }
}));

// Kakao 전략
passport.use(new KakaoStrategy({
    clientID: process.env.KAKAO_CLIENT_ID,
    clientSecret: process.env.KAKAO_CLIENT_SECRET,
    callbackURL: process.env.KAKAO_CALLBACK_URL
}, async (accessToken, refreshToken, profile, done) => {
    try {
        // 기존 사용자 확인
        let user = await User.findOne({ socialId: profile.id, provider: 'kakao' });

        if (!user) {
            // 새 사용자 생성
            user = new User({
                email: profile._json.kakao_account.email,
                name: profile.displayName,
                socialId: profile.id,
                provider: 'kakao',
                profileImage: profile._json.properties.profile_image
            });
            await user.save();
        }

        return done(null, user);
    } catch (error) {
        return done(error, null);
    }
}));

// Naver 전략
passport.use(new NaverStrategy({
    clientID: process.env.NAVER_CLIENT_ID,
    clientSecret: process.env.NAVER_CLIENT_SECRET,
    callbackURL: process.env.NAVER_CALLBACK_URL
}, async (accessToken, refreshToken, profile, done) => {
    try {
        // 기존 사용자 확인
        let user = await User.findOne({ socialId: profile.id, provider: 'naver' });

        if (!user) {
            // 새 사용자 생성
            user = new User({
                email: profile.emails[0].value,
                name: profile.displayName,
                socialId: profile.id,
                provider: 'naver',
                profileImage: profile._json.profile_image
            });
            await user.save();
        }

        return done(null, user);
    } catch (error) {
        return done(error, null);
    }
}));

module.exports = passport; 