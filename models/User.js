const mongoose = require('mongoose');
const bcrypt = require('bcryptjs');

const userSchema = new mongoose.Schema({
    email: {
        type: String,
        required: true,
        unique: true,
        trim: true,
        lowercase: true
    },
    password: {
        type: String,
        required: function() {
            return !this.socialId; // 소셜 로그인의 경우 비밀번호 불필요
        }
    },
    name: {
        type: String,
        trim: true
    },
    socialId: {
        type: String,
        sparse: true
    },
    provider: {
        type: String,
        enum: ['local', 'google', 'kakao', 'naver']
    },
    profileImage: {
        type: String
    },
    sketches: [{
        type: mongoose.Schema.Types.ObjectId,
        ref: 'Sketch'
    }],
    createdAt: {
        type: Date,
        default: Date.now
    },
    lastLogin: {
        type: Date
    }
});

// 비밀번호 해싱
userSchema.pre('save', async function(next) {
    if (this.isModified('password')) {
        this.password = await bcrypt.hash(this.password, 10);
    }
    next();
});

// 비밀번호 검증 메서드
userSchema.methods.comparePassword = async function(candidatePassword) {
    return bcrypt.compare(candidatePassword, this.password);
};

module.exports = mongoose.model('User', userSchema); 