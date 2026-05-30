package me.zipi.navitotesla.ui

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import me.zipi.navitotesla.R

/**
 * maxHeight 속성을 지원하는 RecyclerView. 부모가 ScrollView 또는 wrap_content 인 경우에도
 * 정해진 최대 높이까지만 펼치고 그 이상은 내부 스크롤로 처리한다.
 *
 * 사용 예:
 * ```xml
 * <me.zipi.navitotesla.ui.BoundedRecyclerView
 *     android:layout_height="wrap_content"
 *     app:maxHeight="@dimen/favorite_list_max_height"
 *     android:nestedScrollingEnabled="true" />
 * ```
 */
class BoundedRecyclerView
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
    ) : RecyclerView(context, attrs, defStyleAttr) {
        private var maxHeight: Int = 0

        init {
            if (attrs != null) {
                val ta = context.obtainStyledAttributes(attrs, R.styleable.BoundedRecyclerView)
                maxHeight = ta.getDimensionPixelSize(R.styleable.BoundedRecyclerView_maxHeight, 0)
                ta.recycle()
            }
        }

        override fun onMeasure(
            widthSpec: Int,
            heightSpec: Int,
        ) {
            val newHeightSpec =
                if (maxHeight > 0) {
                    View.MeasureSpec.makeMeasureSpec(maxHeight, View.MeasureSpec.AT_MOST)
                } else {
                    heightSpec
                }
            super.onMeasure(widthSpec, newHeightSpec)
        }
    }
