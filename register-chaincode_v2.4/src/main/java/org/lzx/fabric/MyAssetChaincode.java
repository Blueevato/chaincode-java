package org.example.fabricjava.chaincode;

import com.alibaba.fastjson.JSON;
import org.hyperledger.fabric.contract.Context;
import org.hyperledger.fabric.contract.ContractInterface;
import org.hyperledger.fabric.contract.annotation.*;
import org.hyperledger.fabric.shim.ChaincodeException;
import org.hyperledger.fabric.shim.ChaincodeStub;
import org.hyperledger.fabric.shim.ledger.KeyModification;
import org.hyperledger.fabric.shim.ledger.KeyValue;
import org.hyperledger.fabric.shim.ledger.QueryResultsIterator;
import org.example.fabricjava.chaincode.User;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import java.io.*;
import java.io.File;
import java.io.IOException;
/**
 * 智能合约
 *
 * @author lzx
 * @version 2.2
 * @date 2023/5/28
 */
@Contract(name = "registercc")
@Default
public class MyAssetChaincode implements ContractInterface {
    public  MyAssetChaincode() {}

    /**
     * 初始化3条记录
     */
    @Transaction(intent = Transaction.TYPE.SUBMIT)
    public void init(final Context ctx) {
        addUser(ctx, "1", "trustNode",1000D,"hashCode","训练节点");
        addUser(ctx, "2", "admin",1000D,"hashCode","管理员");
        addUser(ctx, "3", "guest",1000D,"hashCode","未注册用户");
    }

    /**
     * 获取该id的所有变更记录-溯源;记录留痕
     */
    @Transaction(intent = Transaction.TYPE.EVALUATE)
    public String getHistory(final Context ctx, final String userId) {
        Map<String, String> userHistory = new HashMap<>();
        ChaincodeStub stub = ctx.getStub();
        QueryResultsIterator<KeyModification> iterator = stub.getHistoryForKey(userId);
        for (KeyModification result: iterator) {
            userHistory.put(result.getTxId(), result.getStringValue());
        }
        return JSON.toJSONString(userHistory);
    }

    /**
     * 新增元数据new
     * 测试返回fault信息
     */
    @Transaction(intent = Transaction.TYPE.SUBMIT)
    public String addUser(final Context ctx, final String userId, final String name, final double token, final String hashCode, final String description) {
        int flag=0;
        //不能中文!!!!!!会乱码
        if(name.equals("")){
            String errorMessage = String.format("User: %s | does not registered,can not register meta data!", userId);
            throw new ChaincodeException(errorMessage);
            //return newErrorResponse(String.format("非注册用户,无法注册元数据!"),stub.getCreator());
        }
        //test
        String markString = "未注册";
        if(name.equals("guest")){
            flag = 1;
        }
        List<String> newUser = new ArrayList<String>();
        if(flag==0)
            newUser.add("已注册");
        else
            newUser.add(markString);

        ChaincodeStub stub = ctx.getStub();
        User user = new User(userId, name, token, hashCode, description, newUser);
        String userJson = JSON.toJSONString(user);
        stub.putStringState(userId, userJson);
        return "Registration data added successfully! Transaction Hash :"+stub.getTxId();
    }

    /**
     * JSON转换
     */
    //把一个文件中的内容读取成一个String字符串
    public static String readerMethod(File file) throws IOException {
        FileReader fileReader = new FileReader(file);
        Reader reader = new InputStreamReader(new FileInputStream(file), "Utf-8");
        int ch= 0;
        StringBuffer sb = new StringBuffer();
        while((ch = reader.read()) != -1) {
            sb.append((char) ch);
        }
        fileReader.close();
        reader.close();
        String jsonStr = sb.toString();
        return JSON.toJSONString(jsonStr);
    }

    /**
     * 新增元数据 Meta data
     */
    @Transaction(intent = Transaction.TYPE.SUBMIT)
    public String addMeta(final Context ctx, final String userId, final String name, final double token, final String hashCode, final String fileindex) throws IOException{
        File file = new File(fileindex);
        if(!file.exists()){
            String errorMessage = String.format("User: %s | meta data is null!", userId);
            throw new ChaincodeException(errorMessage);
        }
        String res = ""+readerMethod(file);
        return addUser(ctx,userId,name,token,hashCode,res);
    }

    /**
     * 查询某个用户
     */
    @Transaction(intent = Transaction.TYPE.EVALUATE)
    public User getUser(final Context ctx, final String userId) {
        ChaincodeStub stub = ctx.getStub();
        String userJSON = stub.getStringState(userId);
        if (userJSON == null || userJSON.isEmpty()) {
            String errorMessage = String.format("User %s does not exist", userId);
            throw new ChaincodeException(errorMessage);
        }
        return JSON.parseObject(userJSON, User.class);
    }

    /**
     * 数据匹配
     */
    @Transaction(intent = Transaction.TYPE.EVALUATE)
    public String matchDatahash(final Context ctx, final String userId, final String dataHash) {
        ChaincodeStub stub = ctx.getStub();
        String userJSON = stub.getStringState(userId);
        if (userJSON == null || userJSON.isEmpty()) {
            String errorMessage = String.format("User %s does not exist", userId);
            throw new ChaincodeException(errorMessage);
        }
        User uUser = getUser(ctx, userId);
        String hashcode = uUser.gethashCode();
        if(dataHash.equals(uUser.gethashCode())){
            String res= changeState(ctx,userId,2);
            return "Match Success! Transaction Hash："+res;
        }
        else
            return "Error！Match fail! Original dataHash："+hashcode;
    }

    /**
     * 查询所有用户
     */
    @Transaction(intent = Transaction.TYPE.EVALUATE)
    public String queryAll(final Context ctx) {
        ChaincodeStub stub = ctx.getStub();
        List<User> userList = new ArrayList<>();
        QueryResultsIterator<KeyValue> results = stub.getStateByRange("", "");
        for (KeyValue result: results) {
            User user = JSON.parseObject(result.getStringValue(), User.class);
            System.out.println(user);
            userList.add(user);
        }
        return JSON.toJSONString(userList);
    }

    /**
     * 发送数据访问请求
     * 非注册用户无法发送访问请求
     */
    @Transaction(intent = Transaction.TYPE.SUBMIT)
    public String sendRequest(final Context ctx, final String sourceId, final String targetId, final String requestDesc) {
        ChaincodeStub stub = ctx.getStub();
        User sourceUser = getUser(ctx, sourceId);
        User targetUser = getUser(ctx, targetId);
        List<String> res = targetUser.getRequestedId();
        List<String> source = sourceUser.getRequestedId();
        String last = source.get(source.size() - 1);
        if(last.equals("未注册")){
            String errorTxId = changeState(ctx, sourceId, 0);
            String errorMessage = String.format("User: %s | is unregistered!\n Cant get data! returnTxId:"+errorTxId, sourceId);
            throw new ChaincodeException(errorMessage);
        }
        else{
            source.add("已申请数据: "+targetId+", "+requestDesc);
            res.add("被用户: "+sourceId+"申请, "+requestDesc);
            User newSourceUser = new User(sourceUser.getUserId(), sourceUser.getName(), sourceUser.gettoken(), sourceUser.gethashCode(), sourceUser.getDescription(), source);
            User newTargetUser = new User(targetUser.getUserId(), targetUser.getName(), targetUser.gettoken(), targetUser.gethashCode(), targetUser.getDescription(), res);
            stub.putStringState(sourceId, JSON.toJSONString(newSourceUser));
            stub.putStringState(targetId, JSON.toJSONString(newTargetUser));
        }
        return "Send Data Request Success! Transaction Hash :"+stub.getTxId();
    }

    /**
     * 响应数据访问请求
     * 返回token
     */
    @Transaction(intent = Transaction.TYPE.SUBMIT)
    public String feedbackRequest(final Context ctx, final String targetId, final String sourceId, final String FeedbackDesc, final double token) {
        ChaincodeStub stub = ctx.getStub();
        User sourceUser = getUser(ctx, sourceId);
        User targetUser = getUser(ctx, targetId);
        List<String> res = targetUser.getRequestedId();
        List<String> source = sourceUser.getRequestedId();
        source.add("数据: "+targetId+" 请求已被响应, "+FeedbackDesc);
        res.add("用户: "+sourceId+" 请求已受理, "+FeedbackDesc);
        User newSourceUser = new User(sourceUser.getUserId(), sourceUser.getName(), sourceUser.gettoken() + token, sourceUser.gethashCode(), sourceUser.getDescription(), source);
        User newTargetUser = new User(targetUser.getUserId(), targetUser.getName(), targetUser.gettoken(), targetUser.gethashCode(), targetUser.getDescription(), res);
        stub.putStringState(sourceId, JSON.toJSONString(newSourceUser));
        stub.putStringState(targetId, JSON.toJSONString(newTargetUser));
        return "FeedBack Data request Success! Transaction Hash :"+stub.getTxId();
    }

    /**
     * 发送数据训练请求
     * 非注册用户无法发送访问请求
     * @param sourceId 请求方
     * @param targetId 数据方
     * @param chooseNode 选择的训练节点
     * @param TrainDesc 训练任务描述
     */
    @Transaction(intent = Transaction.TYPE.SUBMIT)
    public String sendTrainRequest(final Context ctx, final String sourceId, final String targetId, final  String chooseNode, final String TrainDesc) {
        ChaincodeStub stub = ctx.getStub();
        User sourceUser = getUser(ctx, sourceId);
        User targetUser = getUser(ctx, targetId);
        User chooseNodeUser = getUser(ctx, chooseNode);
        List<String> res = targetUser.getRequestedId();
        List<String> source = sourceUser.getRequestedId();
        List<String> node = chooseNodeUser.getRequestedId();
        String last = source.get(source.size() - 1);
        if(last.equals("未注册")){
            String errorTxId = changeState(ctx, sourceId, 4);
            String errorMessage = String.format("User: %s | is unregistered!\nCant train! returnTxId:"+errorTxId, sourceId);
            throw new ChaincodeException(errorMessage);
        }
        else{
            String targetName = targetUser.getName();
            String sourceName = sourceUser.getName();
            source.add("已申请数据训练任务! 任务描述为： "+TrainDesc);
            res.add("被用户: "+sourceName+"申请作为训练集, 任务描述为： "+TrainDesc);
            node.add("被选中为训练节点！ 训练数据为： "+targetName+",需求方为： "+sourceName);
            User newSourceUser = new User(sourceUser.getUserId(), sourceUser.getName(), sourceUser.gettoken(), sourceUser.gethashCode(), sourceUser.getDescription(), source);
            User newTargetUser = new User(targetUser.getUserId(), targetUser.getName(), targetUser.gettoken(), targetUser.gethashCode(), targetUser.getDescription(), res);
            User newNodeUser = new User(chooseNodeUser.getUserId(), chooseNodeUser.getName(), chooseNodeUser.gettoken(), chooseNodeUser.gethashCode(), chooseNodeUser.getDescription(), node);
            stub.putStringState(sourceId, JSON.toJSONString(newSourceUser));
            stub.putStringState(targetId, JSON.toJSONString(newTargetUser));
            stub.putStringState(chooseNode, JSON.toJSONString(newNodeUser));
        }
        return "Send Train Request Success! Transaction Hash :"+stub.getTxId();
    }

    /**
     * 响应数据训练请求
     * @param sourceId 请求id
     * @param nodeId 节点id
     */
    @Transaction(intent = Transaction.TYPE.SUBMIT)
    public String feedbackTrainRequest(final Context ctx, final String nodeId, final String sourceId, final String FeedbackDesc, final double token) {
        ChaincodeStub stub = ctx.getStub();
        User sourceUser = getUser(ctx, sourceId);
        User targetUser = getUser(ctx, nodeId);
        List<String> resNode = targetUser.getRequestedId();
        List<String> source = sourceUser.getRequestedId();
        source.add("训练任务: "+nodeId+" 请求已被受理, "+FeedbackDesc);
        resNode.add("节点训练中！ "+"  返回参数 ： "+FeedbackDesc);
        User newSourceUser = new User(sourceUser.getUserId(), sourceUser.getName(), sourceUser.gettoken(), sourceUser.gethashCode(), sourceUser.getDescription(), source);
        User newTargetUser = new User(targetUser.getUserId(), targetUser.getName(), targetUser.gettoken() + token, targetUser.gethashCode(), targetUser.getDescription(), resNode);
        stub.putStringState(sourceId, JSON.toJSONString(newSourceUser));
        stub.putStringState(nodeId, JSON.toJSONString(newTargetUser));
        return "FeedBack Train request Success! Transaction Hash "+stub.getTxId();
    }

    /**
     * 训练完成并返回
     * @param sourceId 请求id
     * @param nodeId 节点id
     * @param feedbackHash 训练后的结果哈希
     * @param FeedbackDesc 训练结果描述
     */
    @Transaction(intent = Transaction.TYPE.SUBMIT)
    public String TrainDoneRequest(final Context ctx, final String nodeId, final String sourceId, final String FeedbackDesc, final String feedbackHash) {
        ChaincodeStub stub = ctx.getStub();
        User sourceUser = getUser(ctx, sourceId);
        User targetUser = getUser(ctx, nodeId);
        List<String> resNode = targetUser.getRequestedId();
        List<String> source = sourceUser.getRequestedId();
        resNode.add("节点："+nodeId+" 训练任务完成！ 结果描述为： "+FeedbackDesc);
        source.add("训练任务完结！ 结果哈希为： "+feedbackHash);
        User newSourceUser = new User(sourceUser.getUserId(), sourceUser.getName(), sourceUser.gettoken(), sourceUser.gethashCode(), sourceUser.getDescription(), source);
        User newTargetUser = new User(targetUser.getUserId(), targetUser.getName(), targetUser.gettoken(), targetUser.gethashCode(), targetUser.getDescription(), resNode);
        stub.putStringState(sourceId, JSON.toJSONString(newSourceUser));
        stub.putStringState(nodeId, JSON.toJSONString(newTargetUser));
        return "The training is completed! Transaction Hash :"+stub.getTxId();
    }


    /**
     * token令牌转移
     * @param sourceId 源用户id
     * @param targetId 目标用户id
     * @param token DIY令牌
     */
    @Transaction(intent = Transaction.TYPE.SUBMIT)
    public String transfer(final Context ctx, final String sourceId, final String targetId, final double token) {
        ChaincodeStub stub = ctx.getStub();
        User sourceUser = getUser(ctx, sourceId);
        User targetUser = getUser(ctx, targetId);
        if (sourceUser.gettoken() < token) {
            String errorMessage = String.format("The balance of user %s is insufficient", sourceId);
            throw new ChaincodeException(errorMessage);
        }
        User newSourceUser = new User(sourceUser.getUserId(), sourceUser.getName(), sourceUser.gettoken() - token, sourceUser.gethashCode(), sourceUser.getDescription(), sourceUser.getRequestedId());
        User newTargetUser = new User(targetUser.getUserId(), targetUser.getName(), targetUser.gettoken() + token, targetUser.gethashCode(), targetUser.getDescription(), targetUser.getRequestedId());
        stub.putStringState(sourceId, JSON.toJSONString(newSourceUser));
        stub.putStringState(targetId, JSON.toJSONString(newTargetUser));
        String s="The token is successfully transfer to the target account! Transaction Hash :";
        return ""+s+stub.getTxId();
    }
    /**
     * bad状态变更 done
     * @param sourceId 用户id
     * @param stateInt 需要变更的状态
     */
    @Transaction(intent = Transaction.TYPE.SUBMIT)
    public String changeState(final Context ctx, final String sourceId, final int stateInt) {
        ChaincodeStub stub = ctx.getStub();
        User sourceUser = getUser(ctx, sourceId);
        String[] state= new String[7];
        //状态列表
        state[0]="/非法访问请求!/";
        state[1]="/xxxxxxx/";
        state[2]="/匹配完成！/";
        state[3]="/警告！匹配失败/";
        state[4]="/非法训练请求!/";
        state[5]="/已选择可信节点!训练中/";
        state[6]="/训练完成/";
        List<String> res = sourceUser.getRequestedId();
        res.add(state[stateInt]);
        User newSourceUser = new User(sourceUser.getUserId(),sourceUser.getName(), sourceUser.gettoken(), sourceUser.gethashCode(), sourceUser.getDescription(), res);
        stub.putStringState(sourceId, JSON.toJSONString(newSourceUser));
        String s="Status has been updated! Transaction Hash :";
        return ""+s+stub.getTxId();
    }
    /**
     * String状态变更 
     * @param sourceId 用户id
     * @param stateString String状态
     */
    @Transaction(intent = Transaction.TYPE.SUBMIT)
    public String changeState1(final Context ctx, final String sourceId, final String stateString) {
        ChaincodeStub stub = ctx.getStub();
        User sourceUser = getUser(ctx, sourceId);
        List<String> res = sourceUser.getRequestedId();
        res.add(stateString);
        User newSourceUser = new User(sourceUser.getUserId(),sourceUser.getName(), sourceUser.gettoken(), sourceUser.gethashCode(), sourceUser.getDescription(), res);
        stub.putStringState(sourceId, JSON.toJSONString(newSourceUser));
        String s="Status has been updated! Transaction Hash :";
        return ""+s+stub.getTxId();
    }
}