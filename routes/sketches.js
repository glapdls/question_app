const express = require('express');
const router = express.Router();
const multer = require('multer');
const path = require('path');
const Sketch = require('../models/Sketch');
const auth = require('../middleware/auth');

// 이미지 업로드를 위한 multer 설정
const storage = multer.diskStorage({
    destination: function (req, file, cb) {
        cb(null, 'uploads/');
    },
    filename: function (req, file, cb) {
        cb(null, Date.now() + path.extname(file.originalname));
    }
});

const upload = multer({ storage: storage });

// 스케치 생성
router.post('/', auth, upload.single('image'), async (req, res) => {
    try {
        const { name, description, tags } = req.body;
        const sketch = new Sketch({
            user: req.user._id,
            image: req.file.path,
            name,
            description,
            tags: tags ? tags.split(',').map(tag => tag.trim()) : []
        });

        await sketch.save();
        res.status(201).json(sketch);
    } catch (error) {
        res.status(500).json({ message: '스케치 저장 중 오류가 발생했습니다.' });
    }
});

// 스케치 목록 조회
router.get('/', async (req, res) => {
    try {
        const sketches = await Sketch.find()
            .populate('user', 'name profileImage')
            .sort({ createdAt: -1 });
        res.json(sketches);
    } catch (error) {
        res.status(500).json({ message: '스케치 조회 중 오류가 발생했습니다.' });
    }
});

// 특정 스케치 조회
router.get('/:id', async (req, res) => {
    try {
        const sketch = await Sketch.findById(req.params.id)
            .populate('user', 'name profileImage')
            .populate('comments.user', 'name profileImage');
        
        if (!sketch) {
            return res.status(404).json({ message: '스케치를 찾을 수 없습니다.' });
        }
        
        res.json(sketch);
    } catch (error) {
        res.status(500).json({ message: '스케치 조회 중 오류가 발생했습니다.' });
    }
});

// 스케치 수정
router.put('/:id', auth, async (req, res) => {
    try {
        const sketch = await Sketch.findById(req.params.id);
        
        if (!sketch) {
            return res.status(404).json({ message: '스케치를 찾을 수 없습니다.' });
        }

        if (sketch.user.toString() !== req.user._id.toString()) {
            return res.status(403).json({ message: '수정 권한이 없습니다.' });
        }

        const { name, description, tags } = req.body;
        sketch.name = name || sketch.name;
        sketch.description = description || sketch.description;
        sketch.tags = tags ? tags.split(',').map(tag => tag.trim()) : sketch.tags;

        await sketch.save();
        res.json(sketch);
    } catch (error) {
        res.status(500).json({ message: '스케치 수정 중 오류가 발생했습니다.' });
    }
});

// 스케치 삭제
router.delete('/:id', auth, async (req, res) => {
    try {
        const sketch = await Sketch.findById(req.params.id);
        
        if (!sketch) {
            return res.status(404).json({ message: '스케치를 찾을 수 없습니다.' });
        }

        if (sketch.user.toString() !== req.user._id.toString()) {
            return res.status(403).json({ message: '삭제 권한이 없습니다.' });
        }

        await sketch.remove();
        res.json({ message: '스케치가 삭제되었습니다.' });
    } catch (error) {
        res.status(500).json({ message: '스케치 삭제 중 오류가 발생했습니다.' });
    }
});

// 좋아요 추가/제거
router.post('/:id/like', auth, async (req, res) => {
    try {
        const sketch = await Sketch.findById(req.params.id);
        
        if (!sketch) {
            return res.status(404).json({ message: '스케치를 찾을 수 없습니다.' });
        }

        const likeIndex = sketch.likes.indexOf(req.user._id);
        if (likeIndex === -1) {
            sketch.likes.push(req.user._id);
        } else {
            sketch.likes.splice(likeIndex, 1);
        }

        await sketch.save();
        res.json(sketch);
    } catch (error) {
        res.status(500).json({ message: '좋아요 처리 중 오류가 발생했습니다.' });
    }
});

// 댓글 추가
router.post('/:id/comments', auth, async (req, res) => {
    try {
        const sketch = await Sketch.findById(req.params.id);
        
        if (!sketch) {
            return res.status(404).json({ message: '스케치를 찾을 수 없습니다.' });
        }

        const comment = {
            user: req.user._id,
            text: req.body.text
        };

        sketch.comments.push(comment);
        await sketch.save();

        const populatedSketch = await Sketch.findById(sketch._id)
            .populate('comments.user', 'name profileImage');

        res.json(populatedSketch);
    } catch (error) {
        res.status(500).json({ message: '댓글 추가 중 오류가 발생했습니다.' });
    }
});

module.exports = router; 