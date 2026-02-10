/**
 * WhereHouse 부동산 추천 시스템 - 메인 JavaScript (완전한 통합 버전)
 * Kakao Map API + 사용자 입력 처리 및 추천 결과 표시
 * 전세/월세 분리 API 대응
 */

console.log('스크립트 시작');
console.log('카카오 객체 타입:', typeof kakao);

// 서울시 25개구 정확한 행정구역 경계 좌표 데이터
// 서울시 25개구 정확한 행정구역 경계 좌표 데이터 (기존 간단 버전)
const districtPolygons = {
    '강남구': [
        new kakao.maps.LatLng(37.52867, 127.04766), new kakao.maps.LatLng(37.53265, 127.04946),
        new kakao.maps.LatLng(37.53663, 127.05125), new kakao.maps.LatLng(37.54061, 127.05304),
        new kakao.maps.LatLng(37.54459, 127.05483), new kakao.maps.LatLng(37.54857, 127.05663),
        new kakao.maps.LatLng(37.52265, 127.06242), new kakao.maps.LatLng(37.51868, 127.06063),
        new kakao.maps.LatLng(37.5147, 127.05883), new kakao.maps.LatLng(37.51072, 127.05704),
        new kakao.maps.LatLng(37.50674, 127.05525), new kakao.maps.LatLng(37.50276, 127.05345),
        new kakao.maps.LatLng(37.49878, 127.05166), new kakao.maps.LatLng(37.4948, 127.04987),
        new kakao.maps.LatLng(37.52867, 127.04766)
    ],
    '강동구': [
        new kakao.maps.LatLng(37.55023, 127.12348), new kakao.maps.LatLng(37.55421, 127.12527),
        new kakao.maps.LatLng(37.55819, 127.12706), new kakao.maps.LatLng(37.56217, 127.12886),
        new kakao.maps.LatLng(37.56615, 127.13065), new kakao.maps.LatLng(37.57013, 127.13244),
        new kakao.maps.LatLng(37.52416, 127.13824), new kakao.maps.LatLng(37.52018, 127.13644),
        new kakao.maps.LatLng(37.5162, 127.13465), new kakao.maps.LatLng(37.51222, 127.13286),
        new kakao.maps.LatLng(37.50824, 127.13107), new kakao.maps.LatLng(37.50426, 127.12927),
        new kakao.maps.LatLng(37.50028, 127.12748), new kakao.maps.LatLng(37.4963, 127.12568),
        new kakao.maps.LatLng(37.55023, 127.12348)
    ],
    '강북구': [
        new kakao.maps.LatLng(37.63873, 127.02533), new kakao.maps.LatLng(37.64271, 127.02712),
        new kakao.maps.LatLng(37.64669, 127.02891), new kakao.maps.LatLng(37.65067, 127.03071),
        new kakao.maps.LatLng(37.65465, 127.0325), new kakao.maps.LatLng(37.65863, 127.03429),
        new kakao.maps.LatLng(37.61266, 127.04009), new kakao.maps.LatLng(37.60868, 127.03829),
        new kakao.maps.LatLng(37.6047, 127.0365), new kakao.maps.LatLng(37.60072, 127.03471),
        new kakao.maps.LatLng(37.59674, 127.03291), new kakao.maps.LatLng(37.59276, 127.03112),
        new kakao.maps.LatLng(37.58878, 127.02933), new kakao.maps.LatLng(37.5848, 127.02753),
        new kakao.maps.LatLng(37.63873, 127.02533)
    ],
    '강서구': [
        new kakao.maps.LatLng(37.56108, 126.82259), new kakao.maps.LatLng(37.56506, 126.82438),
        new kakao.maps.LatLng(37.56904, 126.82617), new kakao.maps.LatLng(37.57302, 126.82797),
        new kakao.maps.LatLng(37.577, 126.82976), new kakao.maps.LatLng(37.58098, 126.83155),
        new kakao.maps.LatLng(37.53501, 126.83735), new kakao.maps.LatLng(37.53103, 126.83555),
        new kakao.maps.LatLng(37.52705, 126.83376), new kakao.maps.LatLng(37.52307, 126.83197),
        new kakao.maps.LatLng(37.51909, 126.83017), new kakao.maps.LatLng(37.51511, 126.82838),
        new kakao.maps.LatLng(37.51113, 126.82659), new kakao.maps.LatLng(37.50715, 126.82479),
        new kakao.maps.LatLng(37.56108, 126.82259)
    ],
    '관악구': [
        new kakao.maps.LatLng(37.47954, 126.95443), new kakao.maps.LatLng(37.48352, 126.95622),
        new kakao.maps.LatLng(37.4875, 126.95801), new kakao.maps.LatLng(37.49148, 126.95981),
        new kakao.maps.LatLng(37.49546, 126.9616), new kakao.maps.LatLng(37.49944, 126.96339),
        new kakao.maps.LatLng(37.45347, 126.96919), new kakao.maps.LatLng(37.44949, 126.96739),
        new kakao.maps.LatLng(37.44551, 126.9656), new kakao.maps.LatLng(37.44153, 126.96381),
        new kakao.maps.LatLng(37.43755, 126.96201), new kakao.maps.LatLng(37.43357, 126.96022),
        new kakao.maps.LatLng(37.42959, 126.95843), new kakao.maps.LatLng(37.42561, 126.95663),
        new kakao.maps.LatLng(37.47954, 126.95443)
    ],
    '광진구': [
        new kakao.maps.LatLng(37.54572, 127.08301), new kakao.maps.LatLng(37.5497, 127.0848),
        new kakao.maps.LatLng(37.55368, 127.08659), new kakao.maps.LatLng(37.55766, 127.08839),
        new kakao.maps.LatLng(37.56164, 127.09018), new kakao.maps.LatLng(37.56562, 127.09197),
        new kakao.maps.LatLng(37.51965, 127.09777), new kakao.maps.LatLng(37.51567, 127.09597),
        new kakao.maps.LatLng(37.51169, 127.09418), new kakao.maps.LatLng(37.50771, 127.09239),
        new kakao.maps.LatLng(37.50373, 127.09059), new kakao.maps.LatLng(37.49975, 127.0888),
        new kakao.maps.LatLng(37.49577, 127.08701), new kakao.maps.LatLng(37.49179, 127.08521),
        new kakao.maps.LatLng(37.54572, 127.08301)
    ],
    '구로구': [
        new kakao.maps.LatLng(37.49463, 126.88792), new kakao.maps.LatLng(37.49861, 126.88971),
        new kakao.maps.LatLng(37.50259, 126.8915), new kakao.maps.LatLng(37.50657, 126.8933),
        new kakao.maps.LatLng(37.51055, 126.89509), new kakao.maps.LatLng(37.51453, 126.89688),
        new kakao.maps.LatLng(37.46856, 126.90268), new kakao.maps.LatLng(37.46458, 126.90088),
        new kakao.maps.LatLng(37.4606, 126.89909), new kakao.maps.LatLng(37.45662, 126.8973),
        new kakao.maps.LatLng(37.45264, 126.8955), new kakao.maps.LatLng(37.44866, 126.89371),
        new kakao.maps.LatLng(37.44468, 126.89192), new kakao.maps.LatLng(37.4407, 126.89012),
        new kakao.maps.LatLng(37.49463, 126.88792)
    ],
    '금천구': [
        new kakao.maps.LatLng(37.45677, 126.90292), new kakao.maps.LatLng(37.46075, 126.90471),
        new kakao.maps.LatLng(37.46473, 126.9065), new kakao.maps.LatLng(37.46871, 126.9083),
        new kakao.maps.LatLng(37.47269, 126.91009), new kakao.maps.LatLng(37.47667, 126.91188),
        new kakao.maps.LatLng(37.4307, 126.91768), new kakao.maps.LatLng(37.42672, 126.91588),
        new kakao.maps.LatLng(37.42274, 126.91409), new kakao.maps.LatLng(37.41876, 126.9123),
        new kakao.maps.LatLng(37.41478, 126.9105), new kakao.maps.LatLng(37.4108, 126.90871),
        new kakao.maps.LatLng(37.40682, 126.90692), new kakao.maps.LatLng(37.40284, 126.90512),
        new kakao.maps.LatLng(37.45677, 126.90292)
    ],
    '노원구': [
        new kakao.maps.LatLng(37.65498, 127.05471), new kakao.maps.LatLng(37.65896, 127.0565),
        new kakao.maps.LatLng(37.66294, 127.05829), new kakao.maps.LatLng(37.66692, 127.06009),
        new kakao.maps.LatLng(37.6709, 127.06188), new kakao.maps.LatLng(37.67488, 127.06367),
        new kakao.maps.LatLng(37.62891, 127.06947), new kakao.maps.LatLng(37.62493, 127.06767),
        new kakao.maps.LatLng(37.62095, 127.06588), new kakao.maps.LatLng(37.61697, 127.06409),
        new kakao.maps.LatLng(37.61299, 127.06229), new kakao.maps.LatLng(37.60901, 127.0605),
        new kakao.maps.LatLng(37.60503, 127.05871), new kakao.maps.LatLng(37.60105, 127.05691),
        new kakao.maps.LatLng(37.65498, 127.05471)
    ],
    '도봉구': [
        new kakao.maps.LatLng(37.67902, 127.03242), new kakao.maps.LatLng(37.683, 127.03421),
        new kakao.maps.LatLng(37.68698, 127.036), new kakao.maps.LatLng(37.69096, 127.0378),
        new kakao.maps.LatLng(37.69494, 127.03959), new kakao.maps.LatLng(37.69892, 127.04138),
        new kakao.maps.LatLng(37.65295, 127.04718), new kakao.maps.LatLng(37.64897, 127.04538),
        new kakao.maps.LatLng(37.64499, 127.04359), new kakao.maps.LatLng(37.64101, 127.0418),
        new kakao.maps.LatLng(37.63703, 127.04), new kakao.maps.LatLng(37.63305, 127.03821),
        new kakao.maps.LatLng(37.62907, 127.03642), new kakao.maps.LatLng(37.62509, 127.03462),
        new kakao.maps.LatLng(37.67902, 127.03242)
    ],
    '동대문구': [
        new kakao.maps.LatLng(37.58239, 127.04547), new kakao.maps.LatLng(37.58637, 127.04726),
        new kakao.maps.LatLng(37.59035, 127.04905), new kakao.maps.LatLng(37.59433, 127.05085),
        new kakao.maps.LatLng(37.59831, 127.05264), new kakao.maps.LatLng(37.60229, 127.05443),
        new kakao.maps.LatLng(37.55632, 127.06023), new kakao.maps.LatLng(37.55234, 127.05843),
        new kakao.maps.LatLng(37.54836, 127.05664), new kakao.maps.LatLng(37.54438, 127.05485),
        new kakao.maps.LatLng(37.5404, 127.05305), new kakao.maps.LatLng(37.53642, 127.05126),
        new kakao.maps.LatLng(37.53244, 127.04947), new kakao.maps.LatLng(37.52846, 127.04767),
        new kakao.maps.LatLng(37.58239, 127.04547)
    ],
    '동작구': [
        new kakao.maps.LatLng(37.51242, 126.95443), new kakao.maps.LatLng(37.5164, 126.95622),
        new kakao.maps.LatLng(37.52038, 126.95801), new kakao.maps.LatLng(37.52436, 126.95981),
        new kakao.maps.LatLng(37.52834, 126.9616), new kakao.maps.LatLng(37.53232, 126.96339),
        new kakao.maps.LatLng(37.48635, 126.96919), new kakao.maps.LatLng(37.48237, 126.96739),
        new kakao.maps.LatLng(37.47839, 126.9656), new kakao.maps.LatLng(37.47441, 126.96381),
        new kakao.maps.LatLng(37.47043, 126.96201), new kakao.maps.LatLng(37.46645, 126.96022),
        new kakao.maps.LatLng(37.46247, 126.95843), new kakao.maps.LatLng(37.45849, 126.95663),
        new kakao.maps.LatLng(37.51242, 126.95443)
    ],
    '마포구': [
        new kakao.maps.LatLng(37.56159, 126.88677), new kakao.maps.LatLng(37.56557, 126.88856),
        new kakao.maps.LatLng(37.56955, 126.89035), new kakao.maps.LatLng(37.57353, 126.89215),
        new kakao.maps.LatLng(37.57751, 126.89394), new kakao.maps.LatLng(37.58149, 126.89573),
        new kakao.maps.LatLng(37.53552, 126.90153), new kakao.maps.LatLng(37.53154, 126.89973),
        new kakao.maps.LatLng(37.52756, 126.89794), new kakao.maps.LatLng(37.52358, 126.89615),
        new kakao.maps.LatLng(37.5196, 126.89435), new kakao.maps.LatLng(37.51562, 126.89256),
        new kakao.maps.LatLng(37.51164, 126.89077), new kakao.maps.LatLng(37.50766, 126.88897),
        new kakao.maps.LatLng(37.56159, 126.88677)
    ],
    '서대문구': [
        new kakao.maps.LatLng(37.58439, 126.95443), new kakao.maps.LatLng(37.58837, 126.95622),
        new kakao.maps.LatLng(37.59235, 126.95801), new kakao.maps.LatLng(37.59633, 126.95981),
        new kakao.maps.LatLng(37.60031, 126.9616), new kakao.maps.LatLng(37.60429, 126.96339),
        new kakao.maps.LatLng(37.55832, 126.96919), new kakao.maps.LatLng(37.55434, 126.96739),
        new kakao.maps.LatLng(37.55036, 126.9656), new kakao.maps.LatLng(37.54638, 126.96381),
        new kakao.maps.LatLng(37.5424, 126.96201), new kakao.maps.LatLng(37.53842, 126.96022),
        new kakao.maps.LatLng(37.53444, 126.95843), new kakao.maps.LatLng(37.53046, 126.95663),
        new kakao.maps.LatLng(37.58439, 126.95443)
    ],
    '서초구': [
        new kakao.maps.LatLng(37.47354, 126.98195), new kakao.maps.LatLng(37.47752, 126.98374),
        new kakao.maps.LatLng(37.4815, 126.98553), new kakao.maps.LatLng(37.48548, 126.98733),
        new kakao.maps.LatLng(37.48946, 126.98912), new kakao.maps.LatLng(37.49344, 126.99091),
        new kakao.maps.LatLng(37.44747, 126.99671), new kakao.maps.LatLng(37.44349, 126.99491),
        new kakao.maps.LatLng(37.43951, 126.99312), new kakao.maps.LatLng(37.43553, 126.99133),
        new kakao.maps.LatLng(37.43155, 126.98953), new kakao.maps.LatLng(37.42757, 126.98774),
        new kakao.maps.LatLng(37.42359, 126.98595), new kakao.maps.LatLng(37.41961, 126.98415),
        new kakao.maps.LatLng(37.47354, 126.98195)
    ],
    '성동구': [
        new kakao.maps.LatLng(37.55172, 127.03647), new kakao.maps.LatLng(37.5557, 127.03826),
        new kakao.maps.LatLng(37.55968, 127.04005), new kakao.maps.LatLng(37.56366, 127.04185),
        new kakao.maps.LatLng(37.56764, 127.04364), new kakao.maps.LatLng(37.57162, 127.04543),
        new kakao.maps.LatLng(37.52565, 127.05123), new kakao.maps.LatLng(37.52167, 127.04943),
        new kakao.maps.LatLng(37.51769, 127.04764), new kakao.maps.LatLng(37.51371, 127.04585),
        new kakao.maps.LatLng(37.50973, 127.04405), new kakao.maps.LatLng(37.50575, 127.04226),
        new kakao.maps.LatLng(37.50177, 127.04047), new kakao.maps.LatLng(37.49779, 127.03867),
        new kakao.maps.LatLng(37.55172, 127.03647)
    ],
    '성북구': [
        new kakao.maps.LatLng(37.60704, 127.01847), new kakao.maps.LatLng(37.61102, 127.02026),
        new kakao.maps.LatLng(37.615, 127.02205), new kakao.maps.LatLng(37.61898, 127.02385),
        new kakao.maps.LatLng(37.62296, 127.02564), new kakao.maps.LatLng(37.62694, 127.02743),
        new kakao.maps.LatLng(37.58097, 127.03323), new kakao.maps.LatLng(37.57699, 127.03143),
        new kakao.maps.LatLng(37.57301, 127.02964), new kakao.maps.LatLng(37.56903, 127.02785),
        new kakao.maps.LatLng(37.56505, 127.02605), new kakao.maps.LatLng(37.56107, 127.02426),
        new kakao.maps.LatLng(37.55709, 127.02247), new kakao.maps.LatLng(37.55311, 127.02067),
        new kakao.maps.LatLng(37.60704, 127.01847)
    ],
    '송파구': [
        new kakao.maps.LatLng(37.50334, 127.10598), new kakao.maps.LatLng(37.50732, 127.10777),
        new kakao.maps.LatLng(37.5113, 127.10956), new kakao.maps.LatLng(37.51528, 127.11136),
        new kakao.maps.LatLng(37.51926, 127.11315), new kakao.maps.LatLng(37.52324, 127.11494),
        new kakao.maps.LatLng(37.47727, 127.12074), new kakao.maps.LatLng(37.47329, 127.11894),
        new kakao.maps.LatLng(37.46931, 127.11715), new kakao.maps.LatLng(37.46533, 127.11536),
        new kakao.maps.LatLng(37.46135, 127.11356), new kakao.maps.LatLng(37.45737, 127.11177),
        new kakao.maps.LatLng(37.45339, 127.10998), new kakao.maps.LatLng(37.44941, 127.10818),
        new kakao.maps.LatLng(37.50334, 127.10598)
    ],
    '양천구': [
        new kakao.maps.LatLng(37.52718, 126.85092), new kakao.maps.LatLng(37.53116, 126.85271),
        new kakao.maps.LatLng(37.53514, 126.8545), new kakao.maps.LatLng(37.53912, 126.8563),
        new kakao.maps.LatLng(37.5431, 126.85809), new kakao.maps.LatLng(37.54708, 126.85988),
        new kakao.maps.LatLng(37.50111, 126.86568), new kakao.maps.LatLng(37.49713, 126.86388),
        new kakao.maps.LatLng(37.49315, 126.86209), new kakao.maps.LatLng(37.48917, 126.8603),
        new kakao.maps.LatLng(37.48519, 126.8585), new kakao.maps.LatLng(37.48121, 126.85671),
        new kakao.maps.LatLng(37.47723, 126.85492), new kakao.maps.LatLng(37.47325, 126.85312),
        new kakao.maps.LatLng(37.52718, 126.85092)
    ],
    '영등포구': [
        new kakao.maps.LatLng(37.52318, 126.90677), new kakao.maps.LatLng(37.52716, 126.90856),
        new kakao.maps.LatLng(37.53114, 126.91035), new kakao.maps.LatLng(37.53512, 126.91215),
        new kakao.maps.LatLng(37.5391, 126.91394), new kakao.maps.LatLng(37.54308, 126.91573),
        new kakao.maps.LatLng(37.49711, 126.92153), new kakao.maps.LatLng(37.49313, 126.91973),
        new kakao.maps.LatLng(37.48915, 126.91794), new kakao.maps.LatLng(37.48517, 126.91615),
        new kakao.maps.LatLng(37.48119, 126.91435), new kakao.maps.LatLng(37.47721, 126.91256),
        new kakao.maps.LatLng(37.47323, 126.91077), new kakao.maps.LatLng(37.46925, 126.90897),
        new kakao.maps.LatLng(37.52318, 126.90677)
    ],
    '용산구': [
        new kakao.maps.LatLng(37.54072, 126.96443), new kakao.maps.LatLng(37.5447, 126.96622),
        new kakao.maps.LatLng(37.54868, 126.96801), new kakao.maps.LatLng(37.55266, 126.96981),
        new kakao.maps.LatLng(37.55664, 126.9716), new kakao.maps.LatLng(37.56062, 126.97339),
        new kakao.maps.LatLng(37.51465, 126.97919), new kakao.maps.LatLng(37.51067, 126.97739),
        new kakao.maps.LatLng(37.50669, 126.9756), new kakao.maps.LatLng(37.50271, 126.97381),
        new kakao.maps.LatLng(37.49873, 126.97201), new kakao.maps.LatLng(37.49475, 126.97022),
        new kakao.maps.LatLng(37.49077, 126.96843), new kakao.maps.LatLng(37.48679, 126.96663),
        new kakao.maps.LatLng(37.54072, 126.96443)
    ],
    '은평구': [
        new kakao.maps.LatLng(37.61769, 126.92777), new kakao.maps.LatLng(37.62167, 126.92956),
        new kakao.maps.LatLng(37.62565, 126.93135), new kakao.maps.LatLng(37.62963, 126.93315),
        new kakao.maps.LatLng(37.63361, 126.93494), new kakao.maps.LatLng(37.63759, 126.93673),
        new kakao.maps.LatLng(37.59162, 126.94253), new kakao.maps.LatLng(37.58764, 126.94073),
        new kakao.maps.LatLng(37.58366, 126.93894), new kakao.maps.LatLng(37.57968, 126.93715),
        new kakao.maps.LatLng(37.5757, 126.93535), new kakao.maps.LatLng(37.57172, 126.93356),
        new kakao.maps.LatLng(37.56774, 126.93177), new kakao.maps.LatLng(37.56376, 126.92997),
        new kakao.maps.LatLng(37.61769, 126.92777)
    ],
    '종로구': [
        new kakao.maps.LatLng(37.58239, 126.97443), new kakao.maps.LatLng(37.58637, 126.97622),
        new kakao.maps.LatLng(37.59035, 126.97801), new kakao.maps.LatLng(37.59433, 126.97981),
        new kakao.maps.LatLng(37.59831, 126.9816), new kakao.maps.LatLng(37.60229, 126.98339),
        new kakao.maps.LatLng(37.55632, 126.98919), new kakao.maps.LatLng(37.55234, 126.98739),
        new kakao.maps.LatLng(37.54836, 126.9856), new kakao.maps.LatLng(37.54438, 126.98381),
        new kakao.maps.LatLng(37.5404, 126.98201), new kakao.maps.LatLng(37.53642, 126.98022),
        new kakao.maps.LatLng(37.53244, 126.97843), new kakao.maps.LatLng(37.52846, 126.97663),
        new kakao.maps.LatLng(37.58239, 126.97443)
    ],
    '중구': [
        new kakao.maps.LatLng(37.56139, 126.98943), new kakao.maps.LatLng(37.56537, 126.99122),
        new kakao.maps.LatLng(37.56935, 126.99301), new kakao.maps.LatLng(37.57333, 126.99481),
        new kakao.maps.LatLng(37.57731, 126.9966), new kakao.maps.LatLng(37.58129, 126.99839),
        new kakao.maps.LatLng(37.53532, 127.00419), new kakao.maps.LatLng(37.53134, 127.00239),
        new kakao.maps.LatLng(37.52736, 127.0006), new kakao.maps.LatLng(37.52338, 126.99881),
        new kakao.maps.LatLng(37.5194, 126.99701), new kakao.maps.LatLng(37.51542, 126.99522),
        new kakao.maps.LatLng(37.51144, 126.99343), new kakao.maps.LatLng(37.50746, 126.99163),
        new kakao.maps.LatLng(37.56139, 126.98943)
    ],
    '중랑구': [
        new kakao.maps.LatLng(37.60634, 127.09238), new kakao.maps.LatLng(37.61032, 127.09417),
        new kakao.maps.LatLng(37.6143, 127.09596), new kakao.maps.LatLng(37.61828, 127.09776),
        new kakao.maps.LatLng(37.62226, 127.09955), new kakao.maps.LatLng(37.62624, 127.10134),
        new kakao.maps.LatLng(37.58027, 127.10714), new kakao.maps.LatLng(37.57629, 127.10534),
        new kakao.maps.LatLng(37.57231, 127.10355), new kakao.maps.LatLng(37.56833, 127.10176),
        new kakao.maps.LatLng(37.56435, 127.09996), new kakao.maps.LatLng(37.56037, 127.09817),
        new kakao.maps.LatLng(37.55639, 127.09638), new kakao.maps.LatLng(37.55241, 127.09458),
        new kakao.maps.LatLng(37.60634, 127.09238)
    ]
};

// 폴리곤 저장용 배열
let displayedPolygons = [];

// 전역 변수
let currentRecommendationData = null;
let currentRentalType = null;

// Kakao Map 초기화 및 DOM 로드 완료 시 초기화
window.onload = function() {
    console.log('house_rec.js 로드됨');
    console.log('window.onload 실행됨');
    console.log('카카오 객체 상태:', typeof kakao);

    if (typeof kakao === 'undefined') {
        console.error('카카오맵 API가 로드되지 않았습니다');
        console.log('Network 탭에서 카카오맵 스크립트 로드 상태를 확인하세요');
        return;
    }

    console.log('카카오 maps 객체:', typeof kakao.maps);
    console.log('카카오 LatLng 객체:', typeof kakao.maps.LatLng);

    // 카카오맵 초기화
    var container = document.getElementById("map");
    console.log('map 엘리먼트:', container);

    if (!container) {
        console.error('map 엘리먼트 없음');
        return;
    }

    try {
        var options = {
            center: new kakao.maps.LatLng(37.5642135, 127.0016985),
            level: 9,    // 확대 수준 3으로 설정
        };
        console.log('지도 옵션 설정 완료:', options);

        var map = new kakao.maps.Map(container, options);
        console.log('카카오맵 초기화 성공:', map);

        // 추가적인 제어 비활성화
        // map.setZoomable(false);    // 모든 줌 기능 비활성화
        // map.setDraggable(false);   // 드래그 비활성화

        // 전역 변수로 map 저장
        window.kakaoMap = map;

        // 상세 경계 데이터 미리 로드
        loadDetailedDistrictBoundaries().then(detailedBoundaries => {
            if (detailedBoundaries) {
                console.log('상세 행정구역 경계 데이터 로드 완료');
                // 글로벌 변수에 저장
                window.detailedDistrictPolygons = detailedBoundaries;
            }
        });

        // 초기에는 폴리곤 표시하지 않음

    } catch (error) {
        console.error('카카오맵 초기화 중 오류:', error);
    }

    // 나머지 기능들 초기화
    console.log('DOM 로드 완료, 이벤트 리스너 초기화 시작');
    initializeEventListeners();
    initializeValidation();
    updateSubmitButtonState();
    setupModalEventListeners();
};

console.log('스크립트 파일 끝');

// DOM 로드 완료 시 초기화 (기존 코드와의 호환성 유지)
document.addEventListener('DOMContentLoaded', function() {
    console.log('DOM 로드 완료, 이벤트 리스너 초기화 시작');
    initializeEventListeners();
    initializeValidation();
    updateSubmitButtonState();
    setupModalEventListeners();
});

/**
 * 이벤트 리스너 초기화
 */
function initializeEventListeners() {
    console.log('이벤트 리스너 초기화 중...');

    // 1단계: 기본 조건 설정
    setupRentalTypeHandlers();
    setupBudgetValidation();
    setupAreaValidation();

    // 2단계: 우선순위 설정
    setupPriorityValidation();

    // 3단계: 유연성 설정
    setupFlexibilityHandlers();

    // 추천 결과 확인 버튼
    setupSubmitButton();

    // 뒤로가기 버튼
    setupBackButton();

    console.log('모든 이벤트 리스너 초기화 완료');
}

/**
 * 임대 유형 선택 핸들러
 */
function setupRentalTypeHandlers() {
    const rentalTypeRadios = document.querySelectorAll('input[name="rentalType"]');
    const monthlyExtraFields = document.getElementById('monthlyExtraFields');

    console.log('임대 유형 라디오 버튼 개수:', rentalTypeRadios.length);

    rentalTypeRadios.forEach(radio => {
        radio.addEventListener('change', function() {
            console.log('임대 유형 변경:', this.value);
            currentRentalType = this.value;

            if (this.value === 'MONTHLY') {
                monthlyExtraFields.classList.add('show');
            } else {
                monthlyExtraFields.classList.remove('show');
                // 월세 필드 초기화
                document.getElementById('monthlyMin').value = '';
                document.getElementById('monthlyMax').value = '';
            }
            updateSubmitButtonState();
        });
    });
}

/**
 * 예산 범위 검증 설정
 */
function setupBudgetValidation() {
    const budgetMin = document.getElementById('budgetMin');
    const budgetMax = document.getElementById('budgetMax');
    const monthlyMin = document.getElementById('monthlyMin');
    const monthlyMax = document.getElementById('monthlyMax');
    const budgetError = document.getElementById('budgetError');

    function validateBudget() {
        const minVal = parseInt(budgetMin.value);
        const maxVal = parseInt(budgetMax.value);

        if (budgetMin.value && budgetMax.value) {
            if (minVal > maxVal) {
                showError(budgetError, '최대 예산이 최소 예산보다 크거나 같아야 합니다');
                return false;
            }
        }

        hideError(budgetError);
        return true;
    }

    function validateMonthly() {
        const rentalType = document.querySelector('input[name="rentalType"]:checked')?.value;
        if (rentalType !== 'MONTHLY') return true;

        const minVal = parseInt(monthlyMin.value);
        const maxVal = parseInt(monthlyMax.value);

        if (monthlyMin.value && monthlyMax.value) {
            if (minVal > maxVal) {
                showError(budgetError, '최대 월세가 최소 월세보다 크거나 같아야 합니다');
                return false;
            }
        }

        return true;
    }

    [budgetMin, budgetMax].forEach(input => {
        if (input) {
            input.addEventListener('input', () => {
                validateBudget();
                updateSubmitButtonState();
            });
        }
    });

    [monthlyMin, monthlyMax].forEach(input => {
        if (input) {
            input.addEventListener('input', () => {
                validateMonthly();
                updateSubmitButtonState();
            });
        }
    });
}

/**
 * 평수 범위 검증 설정
 */
function setupAreaValidation() {
    const areaMin = document.getElementById('areaMin');
    const areaMax = document.getElementById('areaMax');
    const areaError = document.getElementById('areaError');

    function validateArea() {
        const minVal = parseFloat(areaMin.value);
        const maxVal = parseFloat(areaMax.value);

        if (areaMin.value && areaMax.value) {
            if (minVal > maxVal) {
                showError(areaError, '최대 평수가 최소 평수보다 크거나 같아야 합니다');
                return false;
            }
        }

        hideError(areaError);
        return true;
    }

    [areaMin, areaMax].forEach(input => {
        if (input) {
            input.addEventListener('input', () => {
                validateArea();
                updateSubmitButtonState();
            });
        }
    });
}

/**
 * 우선순위 선택 검증 설정
 */
function setupPriorityValidation() {
    const priority1 = document.getElementById('priority1');
    const priority2 = document.getElementById('priority2');
    const priority3 = document.getElementById('priority3');
    const priorityError = document.getElementById('priorityError');

    function validatePriorities() {
        const p1 = priority1.value;
        const p2 = priority2.value;
        const p3 = priority3.value;

        if (p1 && p2 && p3) {
            const values = [p1, p2, p3];
            const uniqueValues = [...new Set(values)];

            if (uniqueValues.length !== 3) {
                showError(priorityError, '우선순위는 중복될 수 없습니다');
                return false;
            }
        }

        hideError(priorityError);
        return true;
    }

    [priority1, priority2, priority3].forEach(select => {
        if (select) {
            select.addEventListener('change', () => {
                validatePriorities();
                updateSubmitButtonState();
            });
        }
    });
}

/**
 * 유연성 설정 핸들러
 */
function setupFlexibilityHandlers() {
    // 예산 유연성 슬라이더
    const budgetFlexSlider = document.getElementById('budgetFlexibility');
    const budgetFlexValue = document.getElementById('budgetFlexValue');

    if (budgetFlexSlider && budgetFlexValue) {
        budgetFlexSlider.addEventListener('input', function() {
            budgetFlexValue.textContent = this.value + '%';
        });
    }

    // 최소 안전 점수 슬라이더
    const safetyScoreSlider = document.getElementById('minSafetyScore');
    const safetyScoreValue = document.getElementById('safetyScoreValue');

    if (safetyScoreSlider && safetyScoreValue) {
        safetyScoreSlider.addEventListener('input', function() {
            safetyScoreValue.textContent = this.value + '점';
        });
    }
}

/**
 * 추천 결과 확인 버튼 설정
 */
function setupSubmitButton() {
    let submitButton = document.querySelector('#recommend_result_btn input[type="button"]');

    if (!submitButton) {
        submitButton = document.querySelector('#recommend_result input[type="button"]');
    }

    console.log('제출 버튼 찾기 결과:', submitButton);

    if (submitButton) {
        submitButton.removeEventListener('click', handleSubmitClick);
        submitButton.addEventListener('click', handleSubmitClick);
        console.log('제출 버튼 이벤트 리스너 설정 완료');
    } else {
        console.error('제출 버튼을 찾을 수 없습니다!');
    }
}

/**
 * 제출 버튼 클릭 핸들러
 */
function handleSubmitClick(event) {
    console.log('제출 버튼 클릭됨');
    event.preventDefault();

    const button = event.target;

    if (button.disabled) {
        console.log('버튼이 비활성화 상태입니다');
        return;
    }

    console.log('폼 검증 시작...');
    if (validateAllFields()) {
        console.log('폼 검증 성공, API 요청 시작');
        submitRecommendationRequest();
    } else {
        console.log('폼 검증 실패');
    }
}

/**
 * 뒤로가기 버튼 설정
 */
function setupBackButton() {
    // 전세/월세 결과 페이지의 뒤로가기 버튼들
    const charterBackButton = document.querySelector('#charter_result_title p');
    const monthlyBackButton = document.querySelector('#monthly_result_title p');

    if (charterBackButton) {
        charterBackButton.addEventListener('click', function() {
            showInputPage();
        });
    }

    if (monthlyBackButton) {
        monthlyBackButton.addEventListener('click', function() {
            showInputPage();
        });
    }
}

/**
 * 모든 필드 검증
 */
function validateAllFields() {
    console.log('=== 전체 필드 검증 시작 ===');

    // 1. 임대 유형 검증
    const rentalType = document.querySelector('input[name="rentalType"]:checked')?.value;
    console.log('임대 유형:', rentalType);
    if (!rentalType) {
        alert('임대 유형을 선택해주세요.');
        return false;
    }

    // 2. 예산 범위 검증
    const budgetMin = document.getElementById('budgetMin').value;
    const budgetMax = document.getElementById('budgetMax').value;
    console.log('예산 범위:', budgetMin, '~', budgetMax);

    if (!budgetMin || !budgetMax) {
        alert('예산 범위를 모두 입력해주세요.');
        return false;
    }

    if (parseInt(budgetMin) > parseInt(budgetMax)) {
        alert('최대 예산이 최소 예산보다 크거나 같아야 합니다.');
        return false;
    }

    // 3. 월세 추가 필드 검증 (월세 선택 시)
    if (rentalType === 'MONTHLY') {
        const monthlyMin = document.getElementById('monthlyMin').value;
        const monthlyMax = document.getElementById('monthlyMax').value;
        console.log('월세 범위:', monthlyMin, '~', monthlyMax);

        if (!monthlyMin || !monthlyMax) {
            alert('월세 범위를 입력해주세요.');
            return false;
        }

        if (parseInt(monthlyMin) > parseInt(monthlyMax)) {
            alert('최대 월세가 최소 월세보다 크거나 같아야 합니다.');
            return false;
        }
    }

    // 4. 평수 범위 검증
    const areaMin = document.getElementById('areaMin').value;
    const areaMax = document.getElementById('areaMax').value;
    console.log('평수 범위:', areaMin, '~', areaMax);

    if (!areaMin || !areaMax) {
        alert('평수 범위를 모두 입력해주세요.');
        return false;
    }

    if (parseFloat(areaMin) > parseFloat(areaMax)) {
        alert('최대 평수가 최소 평수보다 크거나 같아야 합니다.');
        return false;
    }

    // 5. 우선순위 검증
    const priority1 = document.getElementById('priority1').value;
    const priority2 = document.getElementById('priority2').value;
    const priority3 = document.getElementById('priority3').value;
    console.log('우선순위:', priority1, priority2, priority3);

    if (!priority1 || !priority2 || !priority3) {
        alert('모든 우선순위를 설정해주세요.');
        return false;
    }

    // 우선순위 중복 검증
    const priorities = [priority1, priority2, priority3];
    const uniquePriorities = [...new Set(priorities)];
    if (uniquePriorities.length !== 3) {
        alert('우선순위는 중복될 수 없습니다.');
        return false;
    }

    console.log('=== 전체 필드 검증 성공 ===');
    return true;
}

/**
 * 제출 버튼 상태 업데이트
 */
function updateSubmitButtonState() {
    let submitButton = document.querySelector('#recommend_result_btn input[type="button"]');

    if (!submitButton) {
        submitButton = document.querySelector('#recommend_result input[type="button"]');
    }

    if (!submitButton) {
        console.warn('제출 버튼을 찾을 수 없어 상태 업데이트를 건너뜁니다.');
        return;
    }

    const isValid = isFormValid();

    submitButton.disabled = !isValid;

    if (isValid) {
        submitButton.style.backgroundColor = '#0B5ED7';
        submitButton.style.cursor = 'pointer';
        submitButton.style.color = 'white';
    } else {
        submitButton.style.backgroundColor = '#ccc';
        submitButton.style.cursor = 'not-allowed';
        submitButton.style.color = '#666';
    }
}

/**
 * 폼 유효성 확인
 */
function isFormValid() {
    const rentalType = document.querySelector('input[name="rentalType"]:checked');
    const budgetMin = document.getElementById('budgetMin').value;
    const budgetMax = document.getElementById('budgetMax').value;
    const areaMin = document.getElementById('areaMin').value;
    const areaMax = document.getElementById('areaMax').value;
    const priority1 = document.getElementById('priority1').value;
    const priority2 = document.getElementById('priority2').value;
    const priority3 = document.getElementById('priority3').value;

    let isValid = rentalType && budgetMin && budgetMax && areaMin && areaMax &&
        priority1 && priority2 && priority3;

    // 월세 선택 시 추가 검증
    if (rentalType?.value === 'MONTHLY') {
        const monthlyMin = document.getElementById('monthlyMin').value;
        const monthlyMax = document.getElementById('monthlyMax').value;
        isValid = isValid && monthlyMin && monthlyMax;
    }

    // 범위 검증
    if (isValid) {
        isValid = parseInt(budgetMax) >= parseInt(budgetMin) &&
            parseFloat(areaMax) >= parseFloat(areaMin);
    }

    // 우선순위 중복 검증
    if (isValid && priority1 && priority2 && priority3) {
        const priorities = [priority1, priority2, priority3];
        const uniquePriorities = [...new Set(priorities)];
        isValid = uniquePriorities.length === 3;
    }

    return isValid;
}

/**
 * 추천 요청 제출 - 전세/월세 분리 API 대응
 */
async function submitRecommendationRequest() {
    try {
        console.log('=== API 요청 시작 ===');

        // 로딩 상태 표시
        showLoading();

        // 임대 유형 확인
        const rentalType = document.querySelector('input[name="rentalType"]:checked').value;
        console.log('선택된 임대 유형:', rentalType);

        // 요청 데이터 구성 및 API 엔드포인트 결정
        let requestData, apiEndpoint;

        if (rentalType === 'CHARTER') {
            // 전세용 요청 데이터 및 엔드포인트
            requestData = buildCharterRequestData();
            apiEndpoint = '/wherehouse/api/recommendations/charter-districts';  // ← 수정
        } else if (rentalType === 'MONTHLY') {
            // 월세용 요청 데이터 및 엔드포인트
            requestData = buildMonthlyRequestData();
            apiEndpoint = '/wherehouse/api/recommendations/monthly-districts';  // ← 수정
        } else {
            throw new Error('올바르지 않은 임대 유형입니다.');
        }

        console.log('API 엔드포인트:', apiEndpoint);
        console.log('전송할 데이터:', requestData);

        // API 요청
        const response = await fetch(apiEndpoint, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Accept': 'application/json'
            },
            body: JSON.stringify(requestData)
        });

        console.log('응답 상태:', response.status);

        if (!response.ok) {
            if (response.status === 400) {
                const errorData = await response.json();
                throw new Error(errorData.message || '입력 데이터에 오류가 있습니다.');
            }
            throw new Error(`서버 오류가 발생했습니다. (${response.status})`);
        }

        const responseData = await response.json();
        console.log('서버 응답:', responseData);

        // 결과 저장 및 화면 표시
        currentRecommendationData = responseData;
        currentRentalType = rentalType;
        showRecommendationResults(responseData, rentalType);

    } catch (error) {
        console.error('추천 요청 오류:', error);
        alert(error.message || '추천 결과를 가져오는 중 오류가 발생했습니다.\n\n네트워크 연결과 서버 상태를 확인해주세요.');
    } finally {
        hideLoading();
    }
}

/**
 * 전세용 요청 데이터 구성
 */
function buildCharterRequestData() {
    const budgetMin = parseInt(document.getElementById('budgetMin').value);
    const budgetMax = parseInt(document.getElementById('budgetMax').value);
    const areaMin = parseFloat(document.getElementById('areaMin').value);
    const areaMax = parseFloat(document.getElementById('areaMax').value);
    const priority1 = document.getElementById('priority1').value;
    const priority2 = document.getElementById('priority2').value;
    const priority3 = document.getElementById('priority3').value;
    const budgetFlexibility = parseInt(document.getElementById('budgetFlexibility').value);
    const minSafetyScore = parseInt(document.getElementById('minSafetyScore').value);
    const absoluteMinArea = parseFloat(document.getElementById('absoluteMinArea').value) || 0.0;

    return {
        budgetMin: budgetMin,           // 전세금 최소
        budgetMax: budgetMax,           // 전세금 최대
        areaMin: areaMin,
        areaMax: areaMax,
        priority1: priority1,
        priority2: priority2,
        priority3: priority3,
        budgetFlexibility: budgetFlexibility,
        minSafetyScore: minSafetyScore,
        absoluteMinArea: absoluteMinArea
    };
}

/**
 * 월세용 요청 데이터 구성
 */
function buildMonthlyRequestData() {
    const budgetMin = parseInt(document.getElementById('budgetMin').value);        // 보증금 최소
    const budgetMax = parseInt(document.getElementById('budgetMax').value);        // 보증금 최대
    const monthlyRentMin = parseInt(document.getElementById('monthlyMin').value);  // 월세금 최소
    const monthlyRentMax = parseInt(document.getElementById('monthlyMax').value);  // 월세금 최대
    const areaMin = parseFloat(document.getElementById('areaMin').value);
    const areaMax = parseFloat(document.getElementById('areaMax').value);
    const priority1 = document.getElementById('priority1').value;
    const priority2 = document.getElementById('priority2').value;
    const priority3 = document.getElementById('priority3').value;
    const budgetFlexibility = parseInt(document.getElementById('budgetFlexibility').value);
    const minSafetyScore = parseInt(document.getElementById('minSafetyScore').value);
    const absoluteMinArea = parseFloat(document.getElementById('absoluteMinArea').value) || 0.0;

    return {
        budgetMin: budgetMin,                 // 보증금 최소
        budgetMax: budgetMax,                 // 보증금 최대
        monthlyRentMin: monthlyRentMin,       // 월세금 최소 (새로 추가된 필드)
        monthlyRentMax: monthlyRentMax,       // 월세금 최대 (새로 추가된 필드)
        areaMin: areaMin,
        areaMax: areaMax,
        priority1: priority1,
        priority2: priority2,
        priority3: priority3,
        budgetFlexibility: budgetFlexibility,
        minSafetyScore: minSafetyScore,
        absoluteMinArea: absoluteMinArea
    };
}

/**
 * 추천 결과 표시 (전세/월세 분기 처리) - 지도 업데이트 포함
 */
/**
 * 추천 결과 표시 (전세/월세 분기 처리) - 지도 업데이트 포함
 */
function showRecommendationResults(data, rentalType) {
    console.log('추천 결과 표시:', data);
    console.log('임대 유형:', rentalType);

    // 추천된 지역구가 있을 때만 지도에 표시
    if (data.recommendedDistricts && data.recommendedDistricts.length > 0) {
        const districtNames = data.recommendedDistricts.map(district => district.district_name);
        console.log('지도에 표시할 추천 지역구들:', districtNames);
        displayRecommendedDistricts(districtNames);
    } else {
        console.log('추천 결과가 없어 지도에 표시할 지역구가 없습니다');
        clearDistrictPolygons(); // 기존 폴리곤 제거
    }

    if (rentalType === 'CHARTER') {
        showCharterResults(data);
    } else if (rentalType === 'MONTHLY') {
        showMonthlyResults(data);
    }
}

/**
 * 전세 결과 표시 - 수정된 버전
 */
function showCharterResults(data) {
    console.log('전세 결과 표시');
    console.log('응답 데이터 구조:', data);

    // 입력 화면 숨기기, 전세 결과 화면 표시
    document.getElementById('user-input').style.display = 'none';
    document.getElementById('charter_result_page').style.display = 'block';
    document.getElementById('monthly_result_page').style.display = 'none';

    // 안내 메시지 업데이트 - searchStatus에 따라 메시지 결정
    const messageElement = document.getElementById('charter_message_text');
    if (messageElement) {
        console.log('data.searchStatus', data.searchStatus);

        if (data.searchStatus === 'NO_RESULTS') {
            // NO_RESULTS일 때는 무조건 "찾을 수 없었습니다" 메시지
            messageElement.textContent = '조건에 맞는 전세 매물을 찾을 수 없었습니다.';
            console.log('NO_RESULTS - 결과 없음 메시지 설정');
        } else if (data.recommendedDistricts && data.recommendedDistricts.length > 0) {
            // 실제로 추천 결과가 있을 때만 "성공적으로 찾았습니다" 메시지
            messageElement.textContent = '조건에 맞는 전세 매물을 성공적으로 찾았습니다.';
            console.log('SUCCESS - 성공 메시지 설정');
        } else {
            // 기타 경우
            messageElement.textContent = '전세 추천 결과가 없습니다.';
            console.log('OTHER - 기타 메시지 설정');
        }
    }

    // 전세 지역구 카드 생성
    renderCharterDistrictCards(data.recommendedDistricts);
}

/**
 * 월세 결과 표시 - 수정된 버전
 */
function showMonthlyResults(data) {
    console.log('월세 결과 표시');

    // 입력 화면 숨기기, 월세 결과 화면 표시
    document.getElementById('user-input').style.display = 'none';
    document.getElementById('charter_result_page').style.display = 'none';
    document.getElementById('monthly_result_page').style.display = 'block';

    const messageElement = document.getElementById('monthly_message_text');
    if (messageElement) {
        if (data.searchStatus === 'NO_RESULTS') {
            messageElement.textContent = '조건에 맞는 월세 매물을 찾을 수 없었습니다.';
        } else if (data.recommendedDistricts && data.recommendedDistricts.length > 0) {
            messageElement.textContent = '조건에 맞는 월세 매물을 성공적으로 찾았습니다.';
        } else {
            messageElement.textContent = '월세 추천 결과가 없습니다.';
        }
    }

    renderMonthlyDistrictCards(data.recommendedDistricts);
}

/**
 * 전세 지역구 카드 렌더링
 */
function renderCharterDistrictCards(districts) {
    const container = document.getElementById('charter_districts_container');
    if (!container) {
        console.error('전세 지역구 컨테이너를 찾을 수 없습니다');
        return;
    }

    container.innerHTML = '';

    if (!districts || districts.length === 0) {
        container.innerHTML = '<div style="text-align: center; padding: 20px;">전세 추천 결과가 없습니다.</div>';
        return;
    }

    districts.forEach((district, index) => {
        const card = createCharterDistrictCard(district, index + 1);
        container.appendChild(card);
    });
}

/**
 * 월세 지역구 카드 렌더링
 */
function renderMonthlyDistrictCards(districts) {
    const container = document.getElementById('monthly_districts_container');
    if (!container) {
        console.error('월세 지역구 컨테이너를 찾을 수 없습니다');
        return;
    }

    container.innerHTML = '';

    if (!districts || districts.length === 0) {
        container.innerHTML = '<div style="text-align: center; padding: 20px;">월세 추천 결과가 없습니다.</div>';
        return;
    }

    districts.forEach((district, index) => {
        const card = createMonthlyDistrictCard(district, index + 1);
        container.appendChild(card);
    });
}

function createCharterDistrictCard(district, rank) {
    const card = document.createElement('div');
    card.className = 'charter_district_card';
    card.id = `charter_district_card_${rank}`;

    const topProperty = district.top_properties && district.top_properties.length > 0
        ? district.top_properties[0] : null;

    const priceText = topProperty ? formatCharterPrice(topProperty) : '매물 정보 없음';
    // 개별 매물 점수 대신 지역구 평균 점수 사용
    const scoreText = district.averageFinalScore ?
        `${Math.floor(district.averageFinalScore * 100) / 100}점` : '-';

    card.innerHTML = `
        <div class="charter_district_header">
            <span class="charter_district_rank">${rank}.</span>
            <span class="charter_district_name">${district.district_name}</span>
        </div>
        
        <div class="charter_district_info">
            <div class="charter_info_row">
                <span class="charter_info_label">대표 매물:</span>
                <span class="charter_info_value charter_price_value">${priceText}</span>
            </div>
            <div class="charter_info_row">
                <span class="charter_info_label">추천 점수:</span>
                <span class="charter_info_value charter_score_value">${scoreText}</span>
            </div>
            <div class="charter_info_row">
                <span class="charter_info_label">추천 근거:</span>
                <span class="charter_info_value">${district.summary || '정보 없음'}</span>
            </div>
        </div>
        
        <div class="charter_district_buttons">
            <button class="charter_btn_detail_rank" onclick="showDetailRankModal('${district.district_name}', 'charter', ${rank})">지역구 추천 정보</button>
            <button class="charter_btn_property_list" onclick="showCharterPropertyListModal('${district.district_name}', ${rank})">상세 매물들 보기</button>
        </div>
    `;

    return card;
}

function createMonthlyDistrictCard(district, rank) {
    const card = document.createElement('div');
    card.className = 'monthly_district_card';
    card.id = `monthly_district_card_${rank}`;

    const topProperty = district.top_properties && district.top_properties.length > 0
        ? district.top_properties[0] : null;

    const priceText = topProperty ? formatMonthlyPrice(topProperty) : '매물 정보 없음';
    // 개별 매물 점수 대신 지역구 평균 점수 사용
    const scoreText = district.averageFinalScore ?
        `${Math.floor(district.averageFinalScore * 100) / 100}점` : '-';

    card.innerHTML = `
        <div class="monthly_district_header">
            <span class="monthly_district_rank">${rank}.</span>
            <span class="monthly_district_name">${district.district_name}</span>
        </div>
        
        <div class="monthly_district_info">
            <div class="monthly_info_row">
                <span class="monthly_info_label">대표 매물:</span>
                <span class="monthly_info_value monthly_price_value">${priceText}</span>
            </div>
            <div class="monthly_info_row">
                <span class="monthly_info_label">추천 점수:</span>
                <span class="monthly_info_value monthly_score_value">${scoreText}</span>
            </div>
            <div class="monthly_info_row">
                <span class="monthly_info_label">추천 근거:</span>
                <span class="monthly_info_value">${district.summary || '정보 없음'}</span>
            </div>
        </div>
        
        <div class="monthly_district_buttons">
            <button class="monthly_btn_detail_rank" onclick="showDetailRankModal('${district.district_name}', 'monthly', ${rank})">지역구 추천 정보</button>
            <button class="monthly_btn_property_list" onclick="showMonthlyPropertyListModal('${district.district_name}', ${rank})">상세 매물들 보기</button>
        </div>
    `;

    return card;
}

/**
 * 전세 가격 형식화
 */
function formatCharterPrice(property) {
    return `전세 ${property.price.toLocaleString()}만원`;
}



/**
 * 입력 페이지 표시
 */
function showInputPage() {
    document.getElementById('user-input').style.display = 'block';
    document.getElementById('charter_result_page').style.display = 'none';
    document.getElementById('monthly_result_page').style.display = 'none';

    // 입력 페이지로 돌아갈 때 지도의 폴리곤 제거
    clearDistrictPolygons();
}
/**
 * 월세 가격 형식화 - 보증금과 월세금 모두 표시
 */
function formatMonthlyPrice(property) {
    // snake_case 필드명 사용
    if (property.monthly_rent) {
        return `보증금 ${property.price.toLocaleString()}만원 / 월세 ${property.monthly_rent.toLocaleString()}만원`;
    } else {
        return `보증금 ${property.price.toLocaleString()}만원`;
    }
}

/**
 * 매물 상세 모달 표시 - 새로운 간단한 버전
 */
function showPropertyModal(districtName, rentalType, rank) {
    console.log(`매물 상세 모달 표시: ${districtName} (${rentalType}) - 순위: ${rank}`);

    const modal = document.getElementById('property_detail_modal');
    if (!modal) {
        console.error('모달을 찾을 수 없습니다!');
        return;
    }

    if (!currentRecommendationData || !currentRecommendationData.recommendedDistricts) {
        console.error('추천 데이터가 없습니다');
        return;
    }

    // 해당 지역구 찾기
    const district = currentRecommendationData.recommendedDistricts.find(d => d.district_name === districtName);
    if (!district) {
        console.error(`지역구를 찾을 수 없습니다: ${districtName}`);
        return;
    }

    // 모달 제목 설정
    const modalTitle = document.getElementById('property_modal_title');
    if (modalTitle) {
        modalTitle.textContent = `${districtName} ${rentalType === 'charter' ? '전세' : '월세'} 매물 목록`;
    }

    // 매물 목록 컨테이너 가져오기
    const container = document.getElementById('property_list_container');
    if (!container) {
        console.error('컨테이너를 찾을 수 없습니다');
        return;
    }

    // 기존 내용 지우기
    container.innerHTML = '';

    // 매물이 있는지 확인
    if (!district.top_properties || district.top_properties.length === 0) {
        container.innerHTML = `
            <div style="text-align: center; padding: 50px 20px; color: #666;">
                <div style="font-size: 48px; margin-bottom: 20px;">🏠</div>
                <div style="font-size: 16px;">해당 지역구에 조건에 맞는 매물이 없습니다.</div>
            </div>
        `;
    } else {
        // 매물 카드들 생성
        district.top_properties.forEach((property, index) => {
            const card = createPropertyCard(property, index + 1, rentalType);
            container.appendChild(card);
        });
    }

    // 모달 표시
    modal.style.display = 'block';
    document.body.classList.add('modal_open');
}

/**
 * 매물 모달 닫기
 */
function closePropertyModal() {
    const modal = document.getElementById('property_detail_modal');
    if (modal) {
        modal.style.display = 'none';
    }
    document.body.classList.remove('modal_open');
}

/**
 * 매물 목록 렌더링 (수정된 버전)
 */
function renderPropertyList(properties, rentalType) {
    console.log('매물 목록 렌더링:', properties, rentalType);

    const container = document.getElementById('property_list_container');
    console.log('매물 리스트 컨테이너:', container);

    if (!container) {
        console.error('property_list_container를 찾을 수 없습니다');
        return;
    }

    container.innerHTML = '';

    if (!properties || properties.length === 0) {
        container.innerHTML = `
            <div class="property_empty_message">
                <div class="property_empty_icon">🏠</div>
                <div class="property_empty_text">해당 지역구에 조건에 맞는 매물이 없습니다.</div>
            </div>
        `;
        console.log('매물이 없어서 빈 메시지 표시');
        return;
    }

    // 점수 순으로 정렬 (높은 점수부터) - 수정된 필드명 사용
    const sortedProperties = [...properties].sort((a, b) => b.finalScore - a.finalScore);
    console.log('정렬된 매물 목록:', sortedProperties);

    sortedProperties.forEach((property, index) => {
        const card = createPropertyCard(property, index + 1, rentalType);
        container.appendChild(card);
    });

    console.log('매물 카드 생성 완료');
}

/**
 * 개별 매물 카드 생성 (수정된 버전)
 */
function createPropertyCard(property, rank, rentalType) {
    const card = document.createElement('div');

    // CSS에 정의된 클래스명 사용
    if (rentalType === 'charter') {
        card.className = 'charter_property_card';
    } else {
        card.className = 'monthly_property_card';
    }

    // 가격 형식화 - 임대 유형별 분리
    const priceText = rentalType === 'charter'
        ? formatCharterPrice(property)
        : formatMonthlyPrice(property);

    // 건축연도 처리 - 수정된 필드명 사용
    const buildYearText = property.buildYear ? `${property.buildYear}년` : '정보없음';

    // 층수 처리
    const floorText = property.floor ? `${property.floor}층` : '정보없음';

    // 전세/월세에 따른 클래스명 선택
    const headerClass = rentalType === 'charter' ? 'charter_property_header' : 'monthly_property_header';
    const titleClass = rentalType === 'charter' ? 'charter_property_title' : 'monthly_property_title';
    const nameClass = rentalType === 'charter' ? 'charter_property_name' : 'monthly_property_name';
    const scoreClass = rentalType === 'charter' ? 'charter_property_score' : 'monthly_property_score';
    const addressClass = rentalType === 'charter' ? 'charter_property_address' : 'monthly_property_address';
    const bodyClass = rentalType === 'charter' ? 'charter_property_body' : 'monthly_property_body';
    const gridClass = rentalType === 'charter' ? 'charter_property_details_grid' : 'monthly_property_details_grid';
    const itemClass = rentalType === 'charter' ? 'charter_property_detail_item' : 'monthly_property_detail_item';
    const labelClass = rentalType === 'charter' ? 'charter_property_detail_label' : 'monthly_property_detail_label';
    const valueClass = rentalType === 'charter' ? 'charter_property_detail_value' : 'monthly_property_detail_value';
    const highlightClass = rentalType === 'charter' ? 'charter_property_price_highlight' : 'monthly_property_price_highlight';

    card.innerHTML = `
        <div class="${headerClass}">
            <div class="${titleClass}">
                <div class="${nameClass}">${property.propertyName || '매물명 없음'}</div>
                <div class="${scoreClass}">${property.finalScore.toFixed(1)}점</div>
            </div>
            <div class="${addressClass}">${property.address || '주소 정보 없음'}</div>
        </div>
        
        <div class="${bodyClass}">
            <div class="${gridClass}">
                <div class="${itemClass} ${highlightClass}">
                    <div class="${labelClass}">가격</div>
                    <div class="${valueClass}">${priceText}</div>
                </div>
                
                <div class="${itemClass}">
                    <div class="${labelClass}">평수</div>
                    <div class="${valueClass}">${property.area}평</div>
                </div>
                
                <div class="${itemClass}">
                    <div class="${labelClass}">건축연도</div>
                    <div class="${valueClass}">${buildYearText}</div>
                </div>
                
                <div class="${itemClass}">
                    <div class="${labelClass}">층수</div>
                    <div class="${valueClass}">${floorText}</div>
                </div>
                
                <div class="${itemClass}">
                    <div class="${labelClass}">임대유형</div>
                    <div class="${valueClass}">${property.leaseType || '정보없음'}</div>
                </div>
                
                <div class="${itemClass}">
                    <div class="${labelClass}">순위</div>
                    <div class="${valueClass}">#${rank}</div>
                </div>
            </div>
        </div>
    `;

    return card;
}

/**
 * 모달 이벤트 리스너 설정 - 상세 순위 모달 추가
 */
function setupModalEventListeners() {
    // 기존 매물 모달
    const propertyModal = document.getElementById('property_detail_modal');
    const propertyOverlay = document.querySelector('.property_modal_overlay');

    if (propertyOverlay) {
        propertyOverlay.addEventListener('click', function(e) {
            if (e.target === propertyOverlay) {
                closePropertyModal();
            }
        });
    }

    // === 2차 명세: 상세 순위 모달 이벤트 리스너 추가 ===
    const rankModal = document.getElementById('detail_rank_modal');
    const rankOverlay = document.querySelector('.detail_rank_modal_overlay');

    if (rankOverlay) {
        rankOverlay.addEventListener('click', function(e) {
            if (e.target === rankOverlay) {
                closeDetailRankModal();
            }
        });
    }

    // ESC 키로 모달 닫기 (기존 코드 수정)
    document.addEventListener('keydown', function(e) {
        if (e.key === 'Escape') {
            if (propertyModal && propertyModal.style.display === 'block') {
                closePropertyModal();
            }
            if (rankModal && rankModal.style.display === 'block') {
                closeDetailRankModal();
            }
        }
    });
}

/**
 * 에러 표시
 */
function showError(errorElement, message) {
    if (errorElement) {
        errorElement.textContent = message;
        errorElement.classList.add('show');
    }
}

/**
 * 에러 숨김
 */
function hideError(errorElement) {
    if (errorElement) {
        errorElement.textContent = '';
        errorElement.classList.remove('show');
    }
}

/**
 * 로딩 상태 표시
 */
function showLoading() {
    let submitButton = document.querySelector('#recommend_result_btn input[type="button"]');

    if (!submitButton) {
        submitButton = document.querySelector('#recommend_result input[type="button"]');
    }

    if (submitButton) {
        submitButton.disabled = true;
        submitButton.value = '추천 결과 검색 중...';
        submitButton.style.backgroundColor = '#ccc';
        submitButton.style.cursor = 'not-allowed';
    }
}

/**
 * 로딩 상태 숨김
 */
function hideLoading() {
    let submitButton = document.querySelector('#recommend_result_btn input[type="button"]');

    if (!submitButton) {
        submitButton = document.querySelector('#recommend_result input[type="button"]');
    }

    if (submitButton) {
        submitButton.disabled = false;
        submitButton.value = '추천 결과 확인';
        updateSubmitButtonState();
    }
}

/**
 * 초기 검증 설정
 */
function initializeValidation() {
    const inputs = document.querySelectorAll('input, select');
    inputs.forEach(input => {
        input.addEventListener('input', updateSubmitButtonState);
        input.addEventListener('change', updateSubmitButtonState);
    });
}

/**
 * 상세 순위 정보 모달 표시 (전세/월세 공통)
 */
function showDetailRankModal(districtName, rentalType, rank) {
    console.log(`상세 순위 정보 모달 표시: ${districtName} (${rentalType}) - 순위: ${rank}`);

    const modal = document.getElementById('detail_rank_modal');
    if (!modal) {
        console.error('상세 순위 모달을 찾을 수 없습니다!');
        return;
    }

    if (!currentRecommendationData || !currentRecommendationData.recommendedDistricts) {
        console.error('추천 데이터가 없습니다');
        return;
    }

    // 해당 지역구 찾기 - 필드명 통일 처리
    const district = currentRecommendationData.recommendedDistricts.find(d =>
        (d.district_name || d.districtName) === districtName
    );

    if (!district) {
        console.error(`지역구를 찾을 수 없습니다: ${districtName}`);
        return;
    }

    // 모달 제목 설정
    const modalTitle = document.getElementById('detail_rank_modal_title');
    if (modalTitle) {
        modalTitle.textContent = `${districtName} 상세 순위 정보`;
    }

    // 기본 정보 설정
    const rankDistrictName = document.getElementById('rank_district_name');
    const rankPosition = document.getElementById('rank_position');

    if (rankDistrictName) rankDistrictName.textContent = districtName;
    if (rankPosition) rankPosition.textContent = `${rank}위`;

    // === 2차 명세: 백엔드에서 추가된 3개 점수 데이터 사용 ===
    // 필드명 호환성 처리 (camelCase와 snake_case 모두 지원)
    const avgPriceScore = district.averagePriceScore || district.average_price_score || 0;
    const avgSpaceScore = district.averageSpaceScore || district.average_space_score || 0;
    const districtSafety = district.districtSafetyScore || district.district_safety_score || 0;

    updateScoreDisplay('average_price_score', 'price_score_bar', avgPriceScore);
    updateScoreDisplay('average_space_score', 'space_score_bar', avgSpaceScore);
    updateScoreDisplay('district_safety_score', 'safety_score_bar', districtSafety);

    // 종합 평가 텍스트 설정
    const summaryText = document.getElementById('rank_summary_text');
    if (summaryText) {
        summaryText.textContent = district.summary || '이 지역구에 대한 상세 평가 정보입니다.';
    }

    // 모달 표시
    modal.style.display = 'block';
    document.body.classList.add('modal_open');
}

/**
 * 점수 표시 업데이트 (점수값과 프로그레스 바)
 */
function updateScoreDisplay(scoreElementId, barElementId, score) {
    // 점수 텍스트 업데이트
    const scoreElement = document.getElementById(scoreElementId);
    if (scoreElement) {
        scoreElement.textContent = `${score.toFixed(1)}점`;
    }

    // 프로그레스 바 업데이트
    const barElement = document.getElementById(barElementId);
    if (barElement) {
        // 0-100 범위로 정규화
        const normalizedScore = Math.max(0, Math.min(100, score));
        barElement.style.width = normalizedScore + '%';
    }
}

/**
 * 상세 순위 모달 닫기
 */
function closeDetailRankModal() {
    const modal = document.getElementById('detail_rank_modal');
    if (modal) {
        modal.style.display = 'none';
    }
    document.body.classList.remove('modal_open');
}

// === 완전히 새로운 매물 리스트 표시 함수들 ===

/**
 * 전세 매물 리스트 모달 표시 (완전히 새로운 함수)
 */
function showCharterPropertyListModal(districtName, rank) {
    console.log(`전세 매물 리스트 모달 표시: ${districtName} - 순위: ${rank}`);

    const modal = document.getElementById('property_detail_modal');
    if (!modal) {
        console.error('매물 모달을 찾을 수 없습니다!');
        return;
    }

    if (!currentRecommendationData || !currentRecommendationData.recommendedDistricts) {
        console.error('추천 데이터가 없습니다');
        return;
    }

    // 해당 지역구 찾기
    const district = currentRecommendationData.recommendedDistricts.find(d => d.district_name === districtName);
    if (!district) {
        console.error(`지역구를 찾을 수 없습니다: ${districtName}`);
        return;
    }

    // 모달 제목 설정
    const modalTitle = document.getElementById('property_modal_title');
    if (modalTitle) {
        modalTitle.textContent = `${districtName} 전세 매물 목록`;
    }

    // 매물 목록 렌더링
    renderCharterPropertyList(district.top_properties || []);

    // 모달 표시
    modal.style.display = 'block';
    document.body.classList.add('modal_open');
}

/**
 * 월세 매물 리스트 모달 표시 (완전히 새로운 함수)
 */
function showMonthlyPropertyListModal(districtName, rank) {
    console.log(`월세 매물 리스트 모달 표시: ${districtName} - 순위: ${rank}`);

    const modal = document.getElementById('property_detail_modal');
    if (!modal) {
        console.error('매물 모달을 찾을 수 없습니다!');
        return;
    }

    if (!currentRecommendationData || !currentRecommendationData.recommendedDistricts) {
        console.error('추천 데이터가 없습니다');
        return;
    }

    // 해당 지역구 찾기
    const district = currentRecommendationData.recommendedDistricts.find(d => d.district_name === districtName);
    if (!district) {
        console.error(`지역구를 찾을 수 없습니다: ${districtName}`);
        return;
    }

    // 모달 제목 설정
    const modalTitle = document.getElementById('property_modal_title');
    if (modalTitle) {
        modalTitle.textContent = `${districtName} 월세 매물 목록`;
    }

    // 매물 목록 렌더링
    renderMonthlyPropertyList(district.top_properties || []);

    // 모달 표시
    modal.style.display = 'block';
    document.body.classList.add('modal_open');
}

/**
 * 전세 매물 목록 렌더링 (완전히 새로운 함수)
 */
function renderCharterPropertyList(properties) {
    const container = document.getElementById('property_list_container');
    if (!container) {
        console.error('property_list_container를 찾을 수 없습니다');
        return;
    }

    container.innerHTML = '';

    if (!properties || properties.length === 0) {
        container.innerHTML = `
            <div style="text-align: center; padding: 50px 20px; color: #666;">
                <div style="font-size: 48px; margin-bottom: 20px;">🏠</div>
                <div style="font-size: 16px;">해당 지역구에 조건에 맞는 전세 매물이 없습니다.</div>
            </div>
        `;
        return;
    }

    // 매물 카드들 생성
    properties.forEach((property, index) => {
        const card = createCharterPropertyCard(property, index + 1);
        container.appendChild(card);
    });
}

/**
 * 월세 매물 목록 렌더링 (완전히 새로운 함수)
 */
function renderMonthlyPropertyList(properties) {
    const container = document.getElementById('property_list_container');
    if (!container) {
        console.error('property_list_container를 찾을 수 없습니다');
        return;
    }

    container.innerHTML = '';

    if (!properties || properties.length === 0) {
        container.innerHTML = `
            <div style="text-align: center; padding: 50px 20px; color: #666;">
                <div style="font-size: 48px; margin-bottom: 20px;">🏠</div>
                <div style="font-size: 16px;">해당 지역구에 조건에 맞는 월세 매물이 없습니다.</div>
            </div>
        `;
        return;
    }

    // 매물 카드들 생성
    properties.forEach((property, index) => {
        const card = createMonthlyPropertyCard(property, index + 1);
        container.appendChild(card);
    });
}

/**
 * 전세 개별 매물 카드 생성 (완전히 새로운 함수)
 */
function createCharterPropertyCard(property, rank) {
    const card = document.createElement('div');
    card.className = 'charter_property_card';

    const priceText = `전세 ${(property.price || 0).toLocaleString()}만원`;
    const buildYearText = property.build_year ? `${property.build_year}년` : '정보없음';
    const floorText = property.floor ? `${property.floor}층` : '정보없음';
    const scoreText = property.final_score ? `${Math.floor(property.final_score * 100) / 100}점` : '-';
    //                                        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ 버림 처리

    card.innerHTML = `
        <div class="charter_property_header">
            <div class="charter_property_title">
                <div class="charter_property_name">${property.property_name || '매물명 없음'}</div>
                <div class="charter_property_score">${scoreText}</div>
            </div>
            <div class="charter_property_address">${property.address || '주소 정보 없음'}</div>
        </div>
        
        <div class="charter_property_body">
            <div class="charter_property_details_grid">
                <div class="charter_property_detail_item charter_property_price_highlight">
                    <div class="charter_property_detail_label">가격</div>
                    <div class="charter_property_detail_value">${priceText}</div>
                </div>
                
                <div class="charter_property_detail_item">
                    <div class="charter_property_detail_label">평수</div>
                    <div class="charter_property_detail_value">${parseFloat(property.area || 0).toFixed(1)}평</div>
                </div>
                
                <div class="charter_property_detail_item">
                    <div class="charter_property_detail_label">건축연도</div>
                    <div class="charter_property_detail_value">${buildYearText}</div>
                </div>
                
                <div class="charter_property_detail_item">
                    <div class="charter_property_detail_label">층수</div>
                    <div class="charter_property_detail_value">${floorText}</div>
                </div>
                
                <div class="charter_property_detail_item">
                    <div class="charter_property_detail_label">임대유형</div>
                    <div class="charter_property_detail_value">${property.lease_type || '전세'}</div>
                </div>
                
                <div class="charter_property_detail_item">
                    <div class="charter_property_detail_label">순위</div>
                    <div class="charter_property_detail_value">#${rank}</div>
                </div>
            </div>
        </div>
    `;

    return card;
}

/**
 * 월세 개별 매물 카드 생성 (완전히 새로운 함수)
 */
function createMonthlyPropertyCard(property, rank) {
    const card = document.createElement('div');
    card.className = 'monthly_property_card';

    const deposit = (property.price || 0).toLocaleString();
    const monthly = (property.monthly_rent || 0).toLocaleString();
    const priceText = `보증금 ${deposit}만원 / 월세 ${monthly}만원`;
    const buildYearText = property.build_year ? `${property.build_year}년` : '정보없음';
    const floorText = property.floor ? `${property.floor}층` : '정보없음';
    const scoreText = property.final_score ? `${Math.floor(property.final_score * 100) / 100}점` : '-';
    //                                        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ 버림 처리

    card.innerHTML = `
        <div class="monthly_property_header">
            <div class="monthly_property_title">
                <div class="monthly_property_name">${property.property_name || '매물명 없음'}</div>
                <div class="monthly_property_score">${scoreText}</div>
            </div>
            <div class="monthly_property_address">${property.address || '주소 정보 없음'}</div>
        </div>
        
        <div class="monthly_property_body">
            <div class="monthly_property_details_grid">
                <div class="monthly_property_detail_item monthly_property_price_highlight">
                    <div class="monthly_property_detail_label">가격</div>
                    <div class="monthly_property_detail_value">${priceText}</div>
                </div>
                
                <div class="monthly_property_detail_item">
                    <div class="monthly_property_detail_label">평수</div>
                    <div class="monthly_property_detail_value">${parseFloat(property.area || 0).toFixed(1)}평</div>
                </div>
                
                <div class="monthly_property_detail_item">
                    <div class="monthly_property_detail_label">건축연도</div>
                    <div class="monthly_property_detail_value">${buildYearText}</div>
                </div>
                
                <div class="monthly_property_detail_item">
                    <div class="monthly_property_detail_label">층수</div>
                    <div class="monthly_property_detail_value">${floorText}</div>
                </div>
                
                <div class="monthly_property_detail_item">
                    <div class="monthly_property_detail_label">임대유형</div>
                    <div class="monthly_property_detail_value">${property.lease_type || '월세'}</div>
                </div>
                
                <div class="monthly_property_detail_item">
                    <div class="monthly_property_detail_label">순위</div>
                    <div class="monthly_property_detail_value">#${rank}</div>
                </div>
            </div>
        </div>
    `;

    return card;
}

/**
 * 정밀한 행정구역 경계로 추천 지역구 표시
 */
async function displayRecommendedDistricts(recommendedDistrictNames) {
    console.log('정밀한 추천 지역구 표시:', recommendedDistrictNames);

    // 기존 폴리곤 제거
    clearDistrictPolygons();

    if (!window.kakaoMap) {
        console.error('카카오맵이 초기화되지 않았습니다');
        return;
    }

    // 상세 경계 데이터 사용 (이미 로드된 경우) 또는 새로 로드
    let detailedBoundaries = window.detailedDistrictPolygons;

    if (!detailedBoundaries) {
        console.log('상세 경계 데이터가 없어 새로 로드합니다...');
        detailedBoundaries = await loadDetailedDistrictBoundaries();
    }

    // 사용할 폴리곤 데이터 결정 (상세 데이터가 있으면 사용, 없으면 기본 데이터)
    const polygonData = detailedBoundaries || districtPolygons;
    const dataType = detailedBoundaries ? '상세' : '기본';
    console.log(`${dataType} 경계 데이터를 사용합니다.`);

    recommendedDistrictNames.forEach(districtName => {
        if (polygonData[districtName]) {
            console.log(`${districtName} ${dataType} 폴리곤 생성 중...`);

            // 폴리곤 생성
            const polygon = new kakao.maps.Polygon({
                path: polygonData[districtName],
                strokeWeight: 2,
                strokeColor: '#FF0000',
                strokeOpacity: 0.8,
                fillColor: '#FF0000',
                fillOpacity: 0.2
            });

            // 지도에 폴리곤 표시
            polygon.setMap(window.kakaoMap);

            // 폴리곤 배열에 저장
            displayedPolygons.push(polygon);

            // 폴리곤 클릭 이벤트
            kakao.maps.event.addListener(polygon, 'click', function() {
                console.log(`${districtName} 폴리곤 클릭됨`);
                if (currentRecommendationData && currentRecommendationData.recommendedDistricts) {
                    const district = currentRecommendationData.recommendedDistricts.find(d => d.district_name === districtName);
                    if (district) {
                        const rank = currentRecommendationData.recommendedDistricts.indexOf(district) + 1;
                        showDetailRankModal(districtName, currentRentalType === 'CHARTER' ? 'charter' : 'monthly', rank);
                    }
                }
            });

            console.log(`${districtName} ${dataType} 폴리곤 표시 완료`);
        } else {
            console.warn(`${districtName}에 대한 폴리곤 데이터가 없습니다`);
        }
    });
}
/**
 * 표시된 모든 지역구 폴리곤 제거
 */
function clearDistrictPolygons() {
    console.log('기존 폴리곤 제거 중...');

    displayedPolygons.forEach(polygon => {
        polygon.setMap(null);
    });

    displayedPolygons = [];
    console.log('폴리곤 제거 완료');
}

/**
 * GitHub에서 정밀한 행정구역 경계 데이터 로드
 */
async function loadDetailedDistrictBoundaries() {
    try {
        console.log('상세 행정구역 경계 데이터 로드 시작...');

        const response = await fetch('https://raw.githubusercontent.com/southkorea/seoul-maps/master/juso/2015/json/seoul_municipalities_geo.json');

        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }

        const geoData = await response.json();
        console.log('GeoJSON 데이터 로드 완료:', geoData);

        // GeoJSON을 카카오맵 폴리곤으로 변환
        const detailedPolygons = {};

        geoData.features.forEach(feature => {
            const districtName = feature.properties.SIG_KOR_NM;
            console.log(`${districtName} 경계 데이터 변환 중...`);

            // MultiPolygon인 경우 첫 번째 폴리곤만 사용
            let coordinates;
            if (feature.geometry.type === 'MultiPolygon') {
                coordinates = feature.geometry.coordinates[0][0]; // 첫 번째 폴리곤의 첫 번째 ring
            } else if (feature.geometry.type === 'Polygon') {
                coordinates = feature.geometry.coordinates[0]; // 첫 번째 ring
            } else {
                console.warn(`지원하지 않는 geometry 타입: ${feature.geometry.type}`);
                return;
            }

            // [lng, lat] -> LatLng(lat, lng) 변환
            const kakaoCoords = coordinates.map(coord =>
                new kakao.maps.LatLng(coord[1], coord[0])
            );

            detailedPolygons[districtName] = kakaoCoords;
            console.log(`${districtName} 변환 완료: ${kakaoCoords.length}개 좌표`);
        });

        console.log('모든 지역구 경계 데이터 변환 완료:', Object.keys(detailedPolygons));
        return detailedPolygons;

    } catch (error) {
        console.error('상세 경계 데이터 로드 실패:', error);
        console.log('기본 경계 데이터를 사용합니다.');
        return null;
    }
}

// 전역 함수로 내보내기
window.showCharterPropertyListModal = showCharterPropertyListModal;
window.showMonthlyPropertyListModal = showMonthlyPropertyListModal;

// 전역 함수로 내보내기
window.showPropertyModal = showPropertyModal;
window.closePropertyModal = closePropertyModal;
// 전역 함수로 내보내기 - 상세 순위 모달 함수 추가
window.showDetailRankModal = showDetailRankModal;
window.closeDetailRankModal = closeDetailRankModal;