/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package com.b44t.messenger.query;

import com.b44t.messenger.MessagesController;
import com.b44t.messenger.NotificationCenter;
import com.b44t.messenger.TLRPC;

import java.util.ArrayList;
import java.util.HashMap;

public class SearchQuery {

    public static ArrayList<TLRPC.TL_topPeer> hints = new ArrayList<>();
    public static ArrayList<TLRPC.TL_topPeer> inlineBots = new ArrayList<>();
    private static HashMap<Integer, Integer> inlineDates = new HashMap<>();
    private static boolean loaded;
    private static boolean loading;

    public static void cleanup() {
        loading = false;
        loaded = false;
        hints.clear();
        inlineBots.clear();
        inlineDates.clear();
        NotificationCenter.getInstance().postNotificationName(NotificationCenter.reloadHints);
        NotificationCenter.getInstance().postNotificationName(NotificationCenter.reloadInlineHints);
    }

    public static void loadHints(boolean cache) {
        if (loading) {
            return;
        }
        if (cache) {
            if (loaded) {
                return;
            }
            loading = true;
            /*
            MessagesStorage.getInstance().getStorageQueue().postRunnable(new Runnable() {
                @Override
                public void run() {
                    final ArrayList<TLRPC.TL_topPeer> hintsNew = new ArrayList<>();
                    final ArrayList<TLRPC.TL_topPeer> inlineBotsNew = new ArrayList<>();
                    final HashMap<Integer, Integer> inlineDatesNew = new HashMap<>();
                    final ArrayList<TLRPC.User> users = new ArrayList<>();
                    final ArrayList<TLRPC.Chat> chats = new ArrayList<>();
                    try {
                        ArrayList<Integer> usersToLoad = new ArrayList<>();
                        ArrayList<Integer> chatsToLoad = new ArrayList<>();
                        SQLiteCursor cursor = MessagesStorage.getInstance().getDatabase().queryFinalized("SELECT did, type, rating, date FROM chat_hints WHERE 1 ORDER BY rating DESC");
                        while (cursor.next()) {
                            int did = cursor.intValue(0);
                            int type = cursor.intValue(1);
                            TLRPC.TL_topPeer peer = new TLRPC.TL_topPeer();
                            peer.rating = cursor.doubleValue(2);
                            if (did > 0) {
                                peer.peer = new TLRPC.TL_peerUser();
                                peer.peer.user_id = did;
                                usersToLoad.add(did);
                            } else {
                                peer.peer = new TLRPC.TL_peerChat();
                                peer.peer.chat_id = -did;
                                chatsToLoad.add(-did);
                            }
                            if (type == 0) {
                                hintsNew.add(peer);
                            } else if (type == 1) {
                                inlineBotsNew.add(peer);
                                inlineDatesNew.put(did, cursor.intValue(3));
                            }
                        }
                        cursor.dispose();
                        if (!usersToLoad.isEmpty()) {
                            //MessagesStorage.getInstance().getUsersInternal(TextUtils.join(",", usersToLoad), users);
                        }

                        if (!chatsToLoad.isEmpty()) {
                            //MessagesStorage.getInstance().getChatsInternal(TextUtils.join(",", chatsToLoad), chats);
                        }
                        AndroidUtilities.runOnUIThread(new Runnable() {
                            @Override
                            public void run() {
                                MessagesController.getInstance().putUsers(users, true);
                                MessagesController.getInstance().putChats(chats, true);
                                loading = false;
                                loaded = true;
                                hints = hintsNew;
                                inlineBots = inlineBotsNew;
                                inlineDates = inlineDatesNew;
                                NotificationCenter.getInstance().postNotificationName(NotificationCenter.reloadHints);
                                NotificationCenter.getInstance().postNotificationName(NotificationCenter.reloadInlineHints);
                                if (Math.abs(UserConfig.lastHintsSyncTime - (int) (System.currentTimeMillis() / 1000)) >= 24 * 60 * 60) {
                                    loadHints(false);
                                }
                            }
                        });
                    } catch (Exception e) {
                        FileLog.e("messenger", e);
                    }
                }
            });
            */
            loaded = true;
        } else {
            loading = true;
            /*
            TLRPC.TL_contacts_getTopPeers req = new TLRPC.TL_contacts_getTopPeers();
            req.hash = 0;
            req.bots_pm = false;
            req.correspondents = true;
            req.groups = false;
            req.channels = false;
            req.bots_inline = true;
            req.offset = 0;
            req.limit = 20;
            ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
                @Override
                public void run(final TLObject response, TLRPC.TL_error error) {
                    if (response instanceof TLRPC.TL_contacts_topPeers) {
                        AndroidUtilities.runOnUIThread(new Runnable() {
                            @Override
                            public void run() {
                                final TLRPC.TL_contacts_topPeers topPeers = (TLRPC.TL_contacts_topPeers) response;
                                MessagesController.getInstance().putUsers(topPeers.users, false);
                                MessagesController.getInstance().putChats(topPeers.chats, false);
                                for (int a = 0; a < topPeers.categories.size(); a++) {
                                    TLRPC.TL_topPeerCategoryPeers category = topPeers.categories.get(a);
                                    if (category.category instanceof TLRPC.TL_topPeerCategoryBotsInline) {
                                        inlineBots = category.peers;
                                    } else {
                                        hints = category.peers;
                                    }
                                }
                                NotificationCenter.getInstance().postNotificationName(NotificationCenter.reloadHints);
                                NotificationCenter.getInstance().postNotificationName(NotificationCenter.reloadInlineHints);
                                final HashMap<Integer, Integer> inlineDatesCopy = new HashMap<>(inlineDates);
                                MessagesStorage.getInstance().getStorageQueue().postRunnable(new Runnable() {
                                    @Override
                                    public void run() {
                                        try {
                                            MessagesStorage.getInstance().getDatabase().executeFast("DELETE FROM chat_hints WHERE 1").stepThis().dispose();
                                            MessagesStorage.getInstance().getDatabase().beginTransaction();
                                            MessagesStorage.getInstance().putUsersAndChats(topPeers.users, topPeers.chats, false, false);

                                            SQLitePreparedStatement state = MessagesStorage.getInstance().getDatabase().executeFast("REPLACE INTO chat_hints VALUES(?, ?, ?, ?)");
                                            for (int a = 0; a < topPeers.categories.size(); a++) {
                                                int type;
                                                TLRPC.TL_topPeerCategoryPeers category = topPeers.categories.get(a);
                                                if (category.category instanceof TLRPC.TL_topPeerCategoryBotsInline) {
                                                    type = 1;
                                                } else {
                                                    type = 0;
                                                }
                                                for (int b = 0; b < category.peers.size(); b++) {
                                                    TLRPC.TL_topPeer peer = category.peers.get(b);
                                                    int did;
                                                    if (peer.peer instanceof TLRPC.TL_peerUser) {
                                                        did = peer.peer.user_id;
                                                    } else if (peer.peer instanceof TLRPC.TL_peerChat) {
                                                        did = -peer.peer.chat_id;
                                                    } else {
                                                        did = -peer.peer.channel_id;
                                                    }
                                                    Integer date = inlineDatesCopy.get(did);
                                                    state.requery();
                                                    state.bindInteger(1, did);
                                                    state.bindInteger(2, type);
                                                    state.bindDouble(3, peer.rating);
                                                    state.bindInteger(4, date != null ? date : 0);
                                                    state.step();
                                                }
                                            }

                                            state.dispose();

                                            MessagesStorage.getInstance().getDatabase().commitTransaction();
                                            AndroidUtilities.runOnUIThread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    UserConfig.lastHintsSyncTime = (int) (System.currentTimeMillis() / 1000);
                                                    UserConfig.saveConfig(false);
                                                }
                                            });
                                        } catch (Exception e) {
                                            FileLog.e("messenger", e);
                                        }
                                    }
                                });
                            }
                        });
                    }
                }
            });*/
        }
    }

    public static void removeInline(final int uid) {
        TLRPC.TL_topPeerCategoryPeers category = null;
        for (int a = 0; a < inlineBots.size(); a++) {
            if (inlineBots.get(a).peer.user_id == uid) {
                inlineBots.remove(a);
                /*TLRPC.TL_contacts_resetTopPeerRating req = new TLRPC.TL_contacts_resetTopPeerRating();
                req.category = new TLRPC.TL_topPeerCategoryBotsInline();
                req.peer = MessagesController.getInputPeer(uid);
                ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
                    @Override
                    public void run(TLObject response, TLRPC.TL_error error) {

                    }
                });
                deletePeer(uid, 1);
                */
                NotificationCenter.getInstance().postNotificationName(NotificationCenter.reloadInlineHints);
                return;
            }
        }
    }

    public static void removePeer(final int uid) {
        TLRPC.TL_topPeerCategoryPeers category = null;
        for (int a = 0; a < hints.size(); a++) {
            if (hints.get(a).peer.user_id == uid) {
                hints.remove(a);
                NotificationCenter.getInstance().postNotificationName(NotificationCenter.reloadHints);
                /*TLRPC.TL_contacts_resetTopPeerRating req = new TLRPC.TL_contacts_resetTopPeerRating();
                req.category = new TLRPC.TL_topPeerCategoryCorrespondents();
                req.peer = MessagesController.getInputPeer(uid);
                deletePeer(uid, 0);
                ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
                    @Override
                    public void run(TLObject response, TLRPC.TL_error error) {

                    }
                });*/
                return;
            }
        }
    }
}
