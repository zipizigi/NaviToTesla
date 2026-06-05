const { onSchedule } = require("firebase-functions/v2/scheduler");
const { logger } = require("firebase-functions");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore, Timestamp } = require("firebase-admin/firestore");

initializeApp();

const STATS_COLLECTION = "stats_collection_counts";

// 루트 컬렉션 전체의 문서 수를 6시간마다 집계해 stats_collection_counts/{ISO시각} 에 적재.
// count() 집계는 인덱스 1,000건당 1 read 라 비용은 사실상 0.
exports.collectionStats = onSchedule(
  {
    schedule: "0 */6 * * *",
    timeZone: "Asia/Seoul",
    region: "asia-northeast3",
    memory: "256MiB",
    timeoutSeconds: 60,
    retryCount: 1,
    // Cloud Scheduler 는 같은 프로젝트라 internal 로 취급됨. 외부 네트워크 접근 차단.
    ingressSettings: "ALLOW_INTERNAL_ONLY",
  },
  async () => {
    const db = getFirestore();
    const collections = await db.listCollections();

    const entries = await Promise.all(
      collections.map(async (col) => {
        const snap = await col.count().get();
        return [col.id, snap.data().count];
      })
    );
    const counts = Object.fromEntries(entries);

    const now = new Date();
    await db.collection(STATS_COLLECTION).doc(now.toISOString()).set({
      counts,
      createdAt: Timestamp.fromDate(now),
    });

    logger.info("collection counts saved", counts);
  }
);
