/**
 * Standalone script to seed knowledge base crops into Firestore
 *
 * Usage:
 *   cd functions
 *   firebase use lumensparkxy  # make sure correct project is selected
 *   npx firebase emulators:exec --only firestore "node seedCrops.js"
 *
 * Or to run against production:
 *   firebase functions:shell
 *   > seedKnowledgeCrops.get()
 *
 * Or deploy the function and call it via the Firebase Console Functions tab.
 */

const admin = require('firebase-admin');

// Initialize with application default credentials
admin.initializeApp();

const db = admin.firestore();
const KNOWLEDGE_CROPS_COLLECTION = 'knowledge_crops';

const cropsData = require('./seeds/knowledge_crops.json');

async function seedCrops() {
  console.log('Starting to seed knowledge crops...');

  const crops = cropsData.crops;
  const batch = db.batch();

  for (const crop of crops) {
    const docRef = db.collection(KNOWLEDGE_CROPS_COLLECTION).doc(crop.id);
    batch.set(docRef, {
      name: crop.name,
      names: crop.names,
      iconUrl: crop.iconUrl,
      displayOrder: crop.displayOrder,
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    }, { merge: true });
    console.log(`Prepared: ${crop.id} - ${crop.name}`);
  }

  await batch.commit();
  console.log(`\n✅ Successfully seeded ${crops.length} crops!`);

  // Print summary
  console.log('\nCrops added:');
  crops.forEach(crop => {
    console.log(`  ${crop.displayOrder}. ${crop.name} (${crop.id})`);
  });
}

seedCrops()
  .then(() => process.exit(0))
  .catch(err => {
    console.error('Error seeding crops:', err);
    process.exit(1);
  });

