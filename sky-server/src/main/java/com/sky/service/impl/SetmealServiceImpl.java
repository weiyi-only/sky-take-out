package com.sky.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.constant.StatusConstant;
import com.sky.dto.SetmealDTO;
import com.sky.dto.SetmealPageQueryDTO;
import com.sky.entity.Dish;
import com.sky.entity.DishFlavor;
import com.sky.entity.Setmeal;
import com.sky.entity.SetmealDish;
import com.sky.exception.DeletionNotAllowedException;
import com.sky.exception.SetmealEnableFailedException;
import com.sky.mapper.DishMapper;
import com.sky.mapper.SetmealDishMapper;
import com.sky.mapper.SetmealMapper;
import com.sky.result.PageResult;
import com.sky.service.SetmealService;
import com.sky.vo.SetmealVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Slf4j
public class SetmealServiceImpl implements SetmealService {
    @Autowired
    private SetmealMapper setmealMapper;
    @Autowired
    private SetmealDishMapper setmealDishMapper;
    @Autowired
    private DishMapper dishMapper;

    @Transactional
    public void save(SetmealDTO setmealDTO) {
        Setmeal setmeal = new Setmeal();
        BeanUtils.copyProperties(setmealDTO, setmeal);
        setmealMapper.insert(setmeal);
        //获取生成的套餐id   通过sql中的useGeneratedKeys="true" keyProperty="id"获取插入后生成的主键值
        //套餐菜品关系表的setmealId页面不能传递，它是向套餐表插入数据之后生成的主键值，也就是套餐菜品关系表的逻辑外键setmealId
        Long setmealId = setmeal.getId();
        //获取页面传来的套餐和菜品关系表数据
        List<SetmealDish> setmealDishes = setmealDTO.getSetmealDishes();
        //遍历关系表数据，为关系表中的每一条数据(每一个对象)的setmealId赋值，
        //   这个地方不需要像之前写新增菜品时多写个if判断，因为之前的口味数据是非必须的，
        //   这个地方要求套餐必须包含菜品是必须的，所以不需要if判断，不存在套餐不包含菜品得情况
        setmealDishes.forEach(setmealDish -> {
            //将Setmeal套餐类的id属性赋值给SetmealDish套餐关系类的setmealId
            //套餐表的id保存在套餐关系表充当外键为setmealId
            setmealDish.setSetmealId(setmealId);
        });
        //保存套餐和菜品的关联关系  动态sql批量插入
        setmealDishMapper.insertBatch(setmealDishes);
    }

    @Override
    public PageResult PageQuery(SetmealPageQueryDTO setmealPageQueryDTO) {
        PageHelper.startPage(setmealPageQueryDTO.getPage(), setmealPageQueryDTO.getPageSize());
        Page<SetmealVO> page = setmealMapper.pageQuery(setmealPageQueryDTO);
        return new PageResult(page.getTotal(), page.getResult());
    }

    @Transactional
    public void delete(List<Long> ids) {
        //不能删除：存在起售中的套餐
        for (Long id : ids) {
            Setmeal setmeal = setmealMapper.getById(id);
            if (setmeal.getStatus() == StatusConstant.ENABLE) { //状态为1起售中
                //当前套餐处于起售中，不能删除
                throw new DeletionNotAllowedException(MessageConstant.SETMEAL_ON_SALE);
            }
        }
        //删除setmeal表和setmeal_dish表中数据
        for (Long id : ids) {
            setmealMapper.deleteById(id);
            //删除口味数据
            setmealDishMapper.deleteByDishId(id);
        }
    }


    public SetmealVO getById(Long id) {
        Setmeal setmeal = setmealMapper.getById(id);
        List<SetmealDish> setmealDishes = setmealDishMapper.getSetmealDishes(id);
        SetmealVO setmealVO = new SetmealVO();
        BeanUtils.copyProperties(setmeal, setmealVO);
        setmealVO.setSetmealDishes(setmealDishes);
        return setmealVO;
    }

    public void update(SetmealDTO setmealDTO) {
        Setmeal setmeal = new Setmeal();
        BeanUtils.copyProperties(setmealDTO, setmeal);
        //修改套餐表基本信息
        setmealMapper.update(setmeal);
        //删除原有的套餐内的菜品消息
        setmealDishMapper.deleteByDishId(setmeal.getId());
        //重新插入套餐内的菜品消息
        List<SetmealDish> setmealDishes = setmealDTO.getSetmealDishes();
        setmealDishes.forEach(setmealDish -> {
            setmealDish.setSetmealId(setmeal.getId());
        });
        setmealDishMapper.insertBatch(setmealDishes);
    }

    @Override
    public void startOrStop(Integer status, Long id) {
        //起售套餐时，判断套餐内是否有停售菜品，有停售菜品提示"套餐内包含未启售菜品，无法启售"
        if (status == StatusConstant.ENABLE) {
            List<Dish> dishList = dishMapper.getBySetmealId(id);
            if (dishList != null && dishList.size() > 0) {
                dishList.forEach(dish -> {
                    if (StatusConstant.DISABLE == dish.getStatus()) {
                        throw new SetmealEnableFailedException(MessageConstant.SETMEAL_ENABLE_FAILED);
                    }
                });
            }
        }
        Setmeal setmeal = Setmeal.builder()
                .status(status)
                .id(id)
                .build();
        setmealMapper.update(setmeal);
    }
}
