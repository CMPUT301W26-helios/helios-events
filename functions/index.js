const functions = require("firebase-functions");
const admin = require("firebase-admin");

admin.initializeApp();

exports.sendPushOnNotification = functions.firestore
  .document("notifications/{notificationId}")
  .onCreate(async (snap) => {
    const notification = snap.data();
    const recipientUid = notification.recipientUid;

    if (!recipientUid) {
      return null;
    }

    const userDoc = await admin.firestore().doc(`users/${recipientUid}`).get();
    if (!userDoc.exists) {
      return null;
    }

    const fcmToken = userDoc.data().fcmToken;
    if (!fcmToken) {
      return null;
    }

    const message = {
      token: fcmToken,
      notification: {
        title: notification.title || "Helios Events",
        body: notification.message || "",
      },
      data: {
        eventId: notification.eventId || "",
        notificationId: snap.id,
      },
    };

    try {
      await admin.messaging().send(message);
    } catch (err) {
      if (err.code === "messaging/registration-token-not-registered") {
        await admin.firestore().doc(`users/${recipientUid}`).update({ fcmToken: null });
      }
    }

    return null;
  });
