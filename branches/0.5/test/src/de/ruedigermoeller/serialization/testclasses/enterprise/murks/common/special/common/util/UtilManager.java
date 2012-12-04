package de.ruedigermoeller.serialization.testclasses.enterprise.murks.common.special.common.util;

import de.ruedigermoeller.serialization.annotations.EqualnessIsBinary;
import de.ruedigermoeller.serialization.testclasses.enterprise.ObjectOrientedDataType;
import de.ruedigermoeller.serialization.testclasses.enterprise.ObjectOrientedInt;
import de.ruedigermoeller.serialization.testclasses.enterprise.SimpleOrder;

import java.util.Date;

/**
 * Created with IntelliJ IDEA.
 * User: ruedi
 * Date: 02.12.12
 * Time: 15:03
 * To change this template use File | Settings | File Templates.
 */
//@EqualnessIsBinary does not pay off
public class UtilManager extends SimpleOrder {

    public UtilManager() {
    }

    public UtilManager(Date mOrderTime, ObjectOrientedDataType mTradableId, ObjectOrientedDataType mTraderId, ObjectOrientedDataType mBuySell, ObjectOrientedDataType mOrderType, ObjectOrientedInt mOrderQty, ObjectOrientedInt mInitialOrderQty, ObjectOrientedInt mOrderPrc, long mOrderId, ObjectOrientedDataType mText) {
        super(mOrderTime, mTradableId, mTraderId, mBuySell, mOrderType, mOrderQty, mInitialOrderQty, mOrderPrc, mOrderId, mText);
    }
}
