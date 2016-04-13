package com.suda.jzapp.manager;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;

import com.avos.avoscloud.AVException;
import com.avos.avoscloud.AVFile;
import com.avos.avoscloud.AVObject;
import com.avos.avoscloud.AVQuery;
import com.avos.avoscloud.AVUser;
import com.avos.avoscloud.FindCallback;
import com.avos.avoscloud.LogInCallback;
import com.avos.avoscloud.SaveCallback;
import com.suda.jzapp.R;
import com.suda.jzapp.dao.cloud.avos.pojo.user.MyAVUser;
import com.suda.jzapp.dao.greendao.User;
import com.suda.jzapp.dao.local.account.AccountLocalDao;
import com.suda.jzapp.dao.local.conf.ConfigLocalDao;
import com.suda.jzapp.dao.local.record.RecordLocalDAO;
import com.suda.jzapp.dao.local.record.RecordTypeLocalDao;
import com.suda.jzapp.dao.local.user.UserLocalDao;
import com.suda.jzapp.misc.Constant;
import com.suda.jzapp.ui.activity.system.SettingsActivity;
import com.suda.jzapp.util.ExceptionInfoUtil;
import com.suda.jzapp.util.ImageUtil;
import com.suda.jzapp.util.LogUtils;
import com.suda.jzapp.util.SPUtils;

import java.util.List;

/**
 * Created by ghbha on 2016/4/6.
 */
public class UserManager extends BaseManager {
    public UserManager(Context context) {
        super(context);
    }

    /**
     * 注册
     *
     * @param userName
     * @param password
     * @param email
     * @param handler
     */
    public void register(String userName, String password, String email, final Handler handler) {
        final MyAVUser user = new MyAVUser();
        Bitmap bitmap = BitmapFactory.decodeResource(_context.getResources(), R.mipmap.suda);
        AVFile avFile = new AVFile(MyAVUser.HEAD_IMAGE, ImageUtil.Bitmap2Bytes(bitmap));
        user.setHeadImage(avFile);
        user.setUsername(userName);
        user.setPassword(password);
        user.setEmail(email);
        user.saveInBackground(new SaveCallback() {
            @Override
            public void done(AVException e) {
                Message message = new Message();
                if (e == null) {
                    message.what = Constant.MSG_SUCCESS;
                } else {
                    message.what = Constant.MSG_ERROR;
                    message.obj = ExceptionInfoUtil.getError(e.getCode());
                    getAvEx(e);
                }
                handler.sendMessage(message);
            }
        });
    }

    /**
     * 登陆
     *
     * @param userName
     * @param password
     * @param email
     * @param handler
     */
    public void login(String userName, final String password, String email, final Handler handler) {
        //邮箱登录
        if (!TextUtils.isEmpty(email)) {
            AVQuery<MyAVUser> query = AVObject.getQuery(MyAVUser.class);
            query.whereEqualTo("email", email);
            query.findInBackground(new FindCallback<MyAVUser>() {
                @Override
                public void done(List<MyAVUser> list, AVException e) {
                    final Message message = new Message();
                    if (e == null) {
                        if (list.size() > 0) {
                            final MyAVUser myAVUser = list.get(0);
                            myAVUser.logInInBackground(myAVUser.getUsername(), password, new LogInCallback<AVUser>() {
                                @Override
                                public void done(AVUser avUser, AVException e) {
                                    if (e == null) {
                                        User user = new User();
                                        user.setUserId(myAVUser.getObjectId());
                                        user.setHeadImage(getImgUrl(myAVUser.getHeadImage()));
                                        user.setUserName(myAVUser.getUsername());
                                        user.setUserCode(myAVUser.getUserCode());
                                        userLocalDao.insertUser(user, _context);
                                        message.what = Constant.MSG_SUCCESS;
                                    } else {
                                        message.what = Constant.MSG_ERROR;
                                        message.obj = ExceptionInfoUtil.getError(e.getCode());
                                        getAvEx(e);
                                    }
                                    handler.sendMessage(message);
                                }
                            });
                        } else {
                            message.what = Constant.MSG_ERROR;
                            message.obj = "未注册";
                            handler.sendMessage(message);
                        }
                    } else {
                        message.what = Constant.MSG_ERROR;
                        message.obj = ExceptionInfoUtil.getError(e.getCode());
                        handler.sendMessage(message);
                        getAvEx(e);
                    }
                }
            });
            return;
        }

        //用户名登录
        AVUser.logInInBackground(userName, password, new LogInCallback<MyAVUser>() {
            @Override
            public void done(MyAVUser avUser, AVException e) {
                Message message = new Message();
                if (e == null) {
                    User user = new User();
                    user.setUserId(avUser.getObjectId());
                    user.setHeadImage(getImgUrl(avUser.getHeadImage()));
                    user.setUserName(avUser.getUsername());
                    userLocalDao.insertUser(user, _context);
                    message.what = Constant.MSG_SUCCESS;
                } else {
                    message.what = Constant.MSG_ERROR;
                    message.obj = ExceptionInfoUtil.getError(e.getCode());
                    getAvEx(e);
                }
                handler.sendMessage(message);
            }
        }, MyAVUser.class);
    }

    public void updateHeadIcon(final AVFile avFile, final Handler handler) {
        AVQuery<MyAVUser> query = AVObject.getQuery(MyAVUser.class);
        query.whereEqualTo("objectId", MyAVUser.getCurrentUser().getObjectId());
        query.findInBackground(new FindCallback<MyAVUser>() {
            @Override
            public void done(List<MyAVUser> list, AVException e) {
                getAvEx(e);
                if (e == null) {
                    MyAVUser avUser = list.get(0);
                    avUser.setHeadImage(avFile);
                    avUser.saveInBackground(new SaveCallback() {
                        @Override
                        public void done(AVException e) {
                            getAvEx(e);
                            if (e == null) {
                                userLocalDao.clear(_context);
                                user = null;
                                sendEmptyMessage(handler, Constant.MSG_SUCCESS);
                            } else {
                                sendEmptyMessage(handler, Constant.MSG_ERROR);
                            }
                        }
                    });
                }
            }
        });
    }

    public void getMe(final Handler handler) {
        final Message message = new Message();
        if (user != null) {
            message.what = Constant.MSG_SUCCESS;
            message.obj = user;
            handler.sendMessage(message);
        } else {
            if (MyAVUser.getCurrentUser() == null) {
                handler.sendEmptyMessage(Constant.MSG_ERROR);
                return;
            }
            user = userLocalDao.getUser(MyAVUser.getCurrentUserId(), _context);
            if (user != null) {
                message.what = Constant.MSG_SUCCESS;
                message.obj = user;
                handler.sendMessage(message);
                return;
            }

            AVQuery<MyAVUser> query = AVObject.getQuery(MyAVUser.class);
            query.whereEqualTo("objectId", MyAVUser.getCurrentUser().getObjectId());
            query.findInBackground(new FindCallback<MyAVUser>() {
                @Override
                public void done(List<MyAVUser> list, AVException e) {
                    if (e == null) {
                        MyAVUser avUser = list.get(0);
                        user = new User();
                        user.setUserId(avUser.getObjectId());
                        user.setHeadImage(getImgUrl(avUser.getHeadImage()));
                        user.setUserCode(avUser.getUserCode());
                        user.setUserName(avUser.getUsername());
                        userLocalDao.insertUser(user, _context);
                        message.what = Constant.MSG_SUCCESS;
                        message.obj = user;
                    } else {
                        message.what = Constant.MSG_ERROR;
                    }
                    handler.sendMessage(message);
                }
            });
        }
    }

    private String getImgUrl(AVFile avFile) {
        if (avFile != null)
            return avFile.getUrl();
        else
            return "";
    }

    private static User user;

    public String getCurUserName() {
        if (MyAVUser.getCurrentUser() != null)
            return MyAVUser.getCurrentUser().getUsername();
        else
            return null;
    }

    /**
     * 用户登出
     */
    public void logOut() {
        logOut(true);
    }

    /**
     * 用户登出
     */
    public void logOut(boolean clearAvUser) {
        if (clearAvUser)
            MyAVUser.getCurrentUser().logOut();
        recordLocalDAO.clearAllRecord(_context);
        accountLocalDao.clearAllAccount(_context);
        recordTypeLocalDao.clearAllRecordType(_context);
        userLocalDao.clear(_context);
        SPUtils.put(_context, Constant.SP_GESTURE, "");
        SPUtils.put(_context, true, SettingsActivity.GESTURE_LOCK, false);
        user = null;
        configLocalDao.initRecordType(_context);
        configLocalDao.createDefaultAccount(_context);
    }

    private UserLocalDao userLocalDao = new UserLocalDao();
    private RecordLocalDAO recordLocalDAO = new RecordLocalDAO();
    private RecordTypeLocalDao recordTypeLocalDao = new RecordTypeLocalDao();
    private AccountLocalDao accountLocalDao = new AccountLocalDao();
    private ConfigLocalDao configLocalDao = new ConfigLocalDao();

}
