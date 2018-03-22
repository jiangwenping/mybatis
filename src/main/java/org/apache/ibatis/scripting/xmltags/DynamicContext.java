/**
 *    Copyright 2009-2015 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.scripting.xmltags;

import java.util.HashMap;
import java.util.Map;

import ognl.OgnlContext;
import ognl.OgnlException;
import ognl.OgnlRuntime;
import ognl.PropertyAccessor;

import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.Configuration;

/**
 * @author Clinton Begin
 */
public class DynamicContext {
  // 在编写映射文件时, '${_parameter}','${_databaseId}'分别可以取到当前用户传入的参数, 以及当前执行的数据库类型
  public static final String PARAMETER_OBJECT_KEY = "_parameter";
  public static final String DATABASE_ID_KEY = "_databaseId";

  static {
    // Mybatis中采用了Ognl来计算动态sql语句，DynamicContext类中的这个静态初始块，很好的说明了这一点
    OgnlRuntime.setPropertyAccessor(ContextMap.class, new ContextAccessor());
  }

  private final ContextMap bindings;
  private final StringBuilder sqlBuilder = new StringBuilder();
  private int uniqueNumber = 0;
  // 构造函数, 对传入的parameterObject对象进行“map”化处理;
  // 也就是说，你传入的pojo对象，会被当作一个键值对数据来源来进行处理，读取这个pojo对象的接口,依然是Map对象(依然是以Map接口方式来进行读取)。
  public DynamicContext(Configuration configuration, Object parameterObject) {
     /*
     * 在DynamicContext的构造函数中，可以看到:
     *    1. 根据传入的参数对象是否为Map类型，有两个不同构造ContextMap的方式。
     *    2. 而ContextMap作为一个继承了HashMap的对象，作用就是用于统一参数的访问方式：用Map接口方法来访问数据。具体来说:
     *         2.1 当传入的参数对象不是Map类型时，Mybatis会将传入的POJO对象用MetaObject对象来封装，
     *         2.2 当动态计算sql过程需要获取数据时，用Map接口的get方法包装 MetaObject对象的取值过程。
     *         2.3 ContextMap覆写的get方法正是为了上述目的.具体参见下面的`ContextMap`覆写的get方法里的详细解释.
     *    3. 这里结合着DefaultSqlSession类中的私有方法wrapCollection一起看效果更佳. wrapCollection方法保证了即使用户传入集合类型时,在构造DynamicContext时使用parameterObject参数依然是个Map类型.
    */

    // 当用户传入的参数是普通的POJO
    if (parameterObject != null && !(parameterObject instanceof Map)) {
      MetaObject metaObject = configuration.newMetaObject(parameterObject);
      bindings = new ContextMap(metaObject);
    } else {
      // 当用户传入的参数null或Map类型时
      bindings = new ContextMap(null);
    }
    bindings.put(PARAMETER_OBJECT_KEY, parameterObject);
    bindings.put(DATABASE_ID_KEY, configuration.getDatabaseId());
  }

  public Map<String, Object> getBindings() {
    return bindings;
  }

  public void bind(String name, Object value) {
    bindings.put(name, value);
  }

  public void appendSql(String sql) {
    sqlBuilder.append(sql);
    sqlBuilder.append(" ");
  }

  public String getSql() {
    return sqlBuilder.toString().trim();
  }

  public int getUniqueNumber() {
    return uniqueNumber++;
  }

  static class ContextMap extends HashMap<String, Object> {
    private static final long serialVersionUID = 2977601501966151582L;

    private MetaObject parameterMetaObject;
    public ContextMap(MetaObject parameterMetaObject) {
      this.parameterMetaObject = parameterMetaObject;
    }

    @Override
    public Object get(Object key) {
      String strKey = (String) key;
      if (super.containsKey(strKey)) {
        return super.get(strKey);
      }

      if (parameterMetaObject != null) {
        // issue #61 do not modify the context when reading
        return parameterMetaObject.getValue(strKey);
      }

      return null;
    }
  }

  static class ContextAccessor implements PropertyAccessor {

    @Override
    public Object getProperty(Map context, Object target, Object name)
        throws OgnlException {
      Map map = (Map) target;

      Object result = map.get(name);
      if (map.containsKey(name) || result != null) {
        return result;
      }

      Object parameterObject = map.get(PARAMETER_OBJECT_KEY);
      if (parameterObject instanceof Map) {
        return ((Map)parameterObject).get(name);
      }

      return null;
    }

    @Override
    public void setProperty(Map context, Object target, Object name, Object value)
        throws OgnlException {
      Map<Object, Object> map = (Map<Object, Object>) target;
      map.put(name, value);
    }

    @Override
    public String getSourceAccessor(OgnlContext arg0, Object arg1, Object arg2) {
      return null;
    }

    @Override
    public String getSourceSetter(OgnlContext arg0, Object arg1, Object arg2) {
      return null;
    }
  }
}