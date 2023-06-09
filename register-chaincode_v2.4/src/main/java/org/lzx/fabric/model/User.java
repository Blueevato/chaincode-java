package org.example.fabricjava.chaincode;

import com.alibaba.fastjson.JSON;
import org.hyperledger.fabric.contract.annotation.DataType;
import org.hyperledger.fabric.contract.annotation.Property;

import java.util.List;
import java.util.Objects;

/**
 * 注册表对象
 *
 * @author lzx
 * @version 2.2
 * @date 2023/5/28
 */
@DataType
public class User {
    @Property
    private final String userId;

    @Property
    private final String name;

    @Property
    private final String description;

    @Property
    private final String hashCode;

    @Property
    private final double token;

    @Property
    private final List<String> requestedId;

    public User(final String userId, final String name,final double token,final String hashCode,final String description, final List<String> requestedId) {
        this.userId = userId;
        this.name = name;
        this.description = description;
        this.hashCode = hashCode;
        this.token = token;
        this.requestedId = requestedId;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if ((obj == null) || (getClass() != obj.getClass())) {
            return false;
        }
        User other = (User) obj;
        return Objects.deepEquals(
                new String[] {getUserId(), getName(), getDescription(), gethashCode()},
                new String[] {other.getUserId(), other.getName(), other.getDescription(), other.gethashCode()})
                &&
                Objects.deepEquals(
                        new double[] {gettoken()},
                        new double[] {other.gettoken()});
    }

    @Override
    public int hashCode() {
        return Objects.hash(getUserId(), getName(), gettoken(), gethashCode(), getDescription());
    }

    @Override
    public String toString() {
        return JSON.toJSONString(this);
    }

    public String getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public String gethashCode() {
        return hashCode;
    }

    public String getDescription() {
        return description;
    }

    public double gettoken() {
        return token;
    }

    public List<String> getRequestedId() {
        return requestedId;
    }
}